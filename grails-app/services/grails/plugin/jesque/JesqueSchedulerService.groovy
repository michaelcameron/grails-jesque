package grails.plugin.jesque

import redis.clients.jedis.Jedis
import grails.converters.JSON
import redis.clients.jedis.Transaction
import redis.clients.jedis.Response
import redis.clients.jedis.Tuple
import org.joda.time.DateTime
import net.greghaines.jesque.Job
import net.greghaines.jesque.json.ObjectMapperFactory

class JesqueSchedulerService {
    static transactionl = false

    def redisService
    def jesqueService
    def grailsApplication

    def schedule(String jobName, String cronExpressionString, String jesqueJobQueue, String jesqueJobName, List args) {
        assert CronExpression.isValidExpression(cronExpressionString)
        CronExpression cronExpression = new CronExpression(cronExpressionString)

        def now = new Date()
        def nextFireTime = cronExpression.getNextValidTimeAfter(now)
        def nextFireTimeLong = nextFireTime.getTime()

        redisService.withRedis{ Jedis redis ->
            //add schedule
            Map<String,String> jobHash = [:]
            jobHash.cronExpression = cronExpressionString
            jobHash.args = new JSON(args).toString()
            jobHash.jesqueJobName = jesqueJobName
            jobHash.jesqueJobQueue = jesqueJobQueue
            redis.hmset("job:$jobName", jobHash )

            //add trigger state
            Map<String,String> triggerHash = [:]
            triggerHash.nextFireTime = nextFireTimeLong.toString()
            triggerHash.state = "WAITING"
            redis.hmset("trigger:$jobName", triggerHash)

            //update trigger indexes
            redis.zadd("trigger:nextFireTime:WAITING:sorted", nextFireTimeLong, jobName)
            redis.zadd("trigger:state:WAITING", 1, jobName)
            /*
            trigger:myJob hash
            trigger:myJob:nextFireTime
            trigger:myJob:lastFireTime
            trigger:myJob:state

            trigger:state:WAITING set
            trigger:state:ACQUIRED set

            trigger:nextFireTime:WAITING:sorted zet

             */
        }
    }

    Integer enqueueReadyJobs(DateTime until) {
        //check to see if there are any servers that have missed removing check-in
        //if so get the intersection of WATIING jobs that are not in the nextFireTimeIndex and add
        List<Tuple> acquiredJobs = acquireJobs(until, 1)

        if( !acquiredJobs )
            return 0

        Long earliestAcquiredJobTime = acquiredJobs.min{ it.score }.score.toLong()
        Long now = DateTime.now().millis
        if( earliestAcquiredJobTime - now > 0 )
            Thread.sleep(earliestAcquiredJobTime - now)

        redisService.withRedis{ Jedis redis ->
            acquiredJobs.element.each{ String jobName ->
                enqueueJob(redis, jobName)
            }
        }

        return acquiredJobs.size()
    }

    List<Tuple> acquireJobs(DateTime until, Integer number) {
        redisService.withRedis { Jedis redis ->
            Set<Tuple> waitingJobs = redis.zrangeWithScores("trigger:nextFireTime:WAITING:sorted", 0, number - 1)
            waitingJobs = waitingJobs.findAll{ it.score <= until.millis }

            if( !waitingJobs )
                return []

            //only get and wait for jobs with the exact time until something like quartz batchTimeWindow is implemented
            def earliestWaitingJobTime = waitingJobs.min{ it.score }.score
            waitingJobs.findAll{ it.score == earliestWaitingJobTime }

            waitingJobs.inject([]){ List<Tuple> acquiredJobsVar, Tuple jobData ->
                def stateKey = "trigger:${jobData.element}:state".toString()
                redis.watch(stateKey)
                Transaction transaction = redis.multi()
                transaction.set(stateKey, "ACQUIRED")
                transaction.zrem("trigger:nextFireTime:WAITING:sorted", jobData.element)
                //transaction.sadd("trigger:nextFireTime:ACQUIRED", jobData.element)
                
                def transactionResult = transaction.exec()
                if( transactionResult != null )
                    acquiredJobsVar << jobData
            } as List<Tuple>
        } as List<Tuple>
    }

    def enqueueJob(Jedis redis, String jobName) {
        Map<String, String> jobHash = redis.hgetAll("job:$jobName")
        CronExpression cronExpression = new CronExpression(jobHash.cronExpression)
        Date nextFireTime = cronExpression.getNextValidTimeAfter(new Date())

        def args = JSON.parse(jobHash.args)
        Job job = new Job(jobHash.jesqueJobName, args )
        String jobJson = ObjectMapperFactory.get().writeValueAsString(job)

        Transaction transaction = redis.multi()
        transaction.sadd("resque:queues", jobHash.jesqueJobQueue)
        transaction.rpush("resque:queue:${jobHash.jesqueJobQueue}", jobJson)
        transaction.set("trigger:$jobName:state", "WAITING")
        transaction.zadd("trigger:nextFireTime:WAITING:sorted", nextFireTime.time, jobName)
        if( transaction.exec() == null )
            throw new Exception("Exception setting job $jobName back to waiting state")
    }
}
