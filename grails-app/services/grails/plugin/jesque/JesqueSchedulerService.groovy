package grails.plugin.jesque

import redis.clients.jedis.Jedis
import grails.converters.JSON
import redis.clients.jedis.Transaction

import redis.clients.jedis.Tuple
import org.joda.time.DateTime
import net.greghaines.jesque.Job
import net.greghaines.jesque.json.ObjectMapperFactory
import org.joda.time.Seconds

class JesqueSchedulerService {
    static transactionl = false

    def redisService
    def jesqueService
    def grailsApplication

    protected static final String JOB_PREFIX = 'job'
    protected static final String TRIGGER_PREFIX = 'trigger'
    protected static final String SCHEDULER_PREFIX = 'scheduler'
    protected static final String TRIGGER_NEXTFIRE_INDEX = 'trigger:nextFireTime:WAITING:sorted'

    protected static final Integer STALE_SERVER_SECONDS = 30

    void schedule(String jobName, String cronExpressionString, String jesqueJobQueue, String jesqueJobName, List args) {
        assert CronExpression.isValidExpression(cronExpressionString)
        CronExpression cronExpression = new CronExpression(cronExpressionString)

        def now = new Date()
        def nextFireTime = cronExpression.getNextValidTimeAfter(now)
        def nextFireTimeLong = nextFireTime.getTime()

        redisService.withRedis{ Jedis redis ->
            //add schedule
            Map<String,String> jobHash = [:]
            jobHash[JobField.CronExpression.name] = cronExpressionString
            jobHash[JobField.Args.name] = new JSON(args).toString()
            jobHash[JobField.JesqueJobName.name] = jesqueJobName
            jobHash[JobField.JesqueJobQueue.name] = jesqueJobQueue
            redis.hmset("$JOB_PREFIX:$jobName", jobHash )

            //add trigger state
            Map<String,String> triggerHash = [:]
            triggerHash[TriggerField.NextFireTime.name] = nextFireTimeLong.toString()
            triggerHash[TriggerField.State.name] = TriggerState.Waiting.name
            triggerHash[TriggerField.AcquiredBy.name] = ""
            redis.hmset("$TRIGGER_PREFIX:$jobName", triggerHash)

            //update trigger indexes
            redis.zadd(TRIGGER_NEXTFIRE_INDEX, nextFireTimeLong, jobName)
        }
    }

    Integer enqueueReadyJobs(DateTime until, String hostName) {
        //check to see if there are any servers that have missed removing check-in
        //if so get the intersection of WATIING jobs that are not in the nextFireTimeIndex and add
        List<Tuple> acquiredJobs = acquireJobs(until, 1, hostName)

        if( !acquiredJobs )
            return 0

        Long earliestAcquiredJobTime = acquiredJobs.min{ it.score }.score.toLong()
        Long now = DateTime.now().millis
        if( earliestAcquiredJobTime - now > 0 ) {
            log.debug "Waiting to fire time to enqueue jobs"
            Thread.sleep(earliestAcquiredJobTime - now)
        }

        redisService.withRedis{ Jedis redis ->
            acquiredJobs.element.each{ String jobName ->
                enqueueJob(redis, jobName, hostName)
            }
        }

        return acquiredJobs.size()
    }

    List<Tuple> acquireJobs(DateTime until, Integer number, String hostName) {
        redisService.withRedis { Jedis redis ->
            Set<Tuple> waitingJobs = redis.zrangeWithScores(TRIGGER_NEXTFIRE_INDEX, 0, number - 1)
            waitingJobs = waitingJobs.findAll{ it.score <= until.millis }

            if( !waitingJobs )
                return []

            //only get and wait for jobs with the exact time until something like quartz batchTimeWindow is implemented
            def earliestWaitingJobTime = waitingJobs.min{ it.score }.score
            waitingJobs.findAll{ it.score == earliestWaitingJobTime }

            //lock jobs
            waitingJobs.inject([]){ List<Tuple> acquiredJobsVar, Tuple jobData ->
                String jobName = jobData.element
                String triggerStateKey = "$TRIGGER_PREFIX:${jobName}"
                redis.watch(triggerStateKey)

                Transaction transaction = redis.multi()
                Map<String, String> triggerHash = [:]
                triggerHash[TriggerField.State.name] = TriggerState.Acquired.name
                triggerHash[TriggerField.AcquiredBy.name] = hostName
                transaction.hmset(triggerStateKey, triggerHash)
                transaction.zrem(TRIGGER_NEXTFIRE_INDEX, jobName)
                transaction.sadd(getAcquiredIndexByHostName(hostName), jobName)

                def transactionResult = transaction.exec()
                if( transactionResult != null )
                    acquiredJobsVar << jobData
                else
                    log.debug "Could not acquire job ${jobName} due to trigger state change"
            } as List<Tuple>
        } as List<Tuple>
    }

    void enqueueJob(Jedis redis, String jobName, String hostName) {
        log.info "Enqueuing job $jobName"
        def jobHash = redis.hgetAll("$JOB_PREFIX:$jobName")
        def cronExpression = new CronExpression(jobHash[JobField.CronExpression.name])
        def nextFireTime = cronExpression.getNextValidTimeAfter(new Date())

        def args = JSON.parse(jobHash.args)
        def job = new Job(jobHash[JobField.JesqueJobName.name], args )
        String jobJson = ObjectMapperFactory.get().writeValueAsString(job)

        Map<String,String> newTriggerHash = [:]
        newTriggerHash[TriggerField.NextFireTime.name] = nextFireTime.time.toString()
        newTriggerHash[TriggerField.State.name] = TriggerState.Waiting.name
        newTriggerHash[TriggerField.AcquiredBy.name] = ""

        String jesqueJobQueue = jobHash[JobField.JesqueJobQueue.name]

        redis.watch("$TRIGGER_PREFIX:$jobName")
        Map<String, String> existingTriggerHash = redis.hgetAll("$TRIGGER_PREFIX:$jobName")
        
        if( existingTriggerHash[TriggerField.State.name] != TriggerState.Acquired.name ) {
            log.warn "Trigger state is no longer ${TriggerState.Acquired.name} for job $jobName"
            redis.unwatch()
            return
        }
        if( existingTriggerHash[TriggerField.AcquiredBy.name] != hostName ) {
            log.warn "Trigger state was acquired by another server ${existingTriggerHash[TriggerField.AcquiredBy.name]} for job $jobName"
            redis.unwatch()
            return
        }

        def transaction = redis.multi()
        transaction.sadd("resque:queues", jesqueJobQueue)
        transaction.rpush("resque:queue:$jesqueJobQueue", jobJson)
        transaction.hmset("$TRIGGER_PREFIX:$jobName", newTriggerHash)
        transaction.zadd(TRIGGER_NEXTFIRE_INDEX, nextFireTime.time, jobName)
        if( transaction.exec() == null )
            log.warn "Job exection aborted for $jobName because the trigger data changed"

        //always remove index regardless of the result of the 
        redis.srem(getAcquiredIndexByHostName(hostName), jobName)
    }

    void serverCheckIn(String hostName, DateTime checkInDate) {
        //TODO: detect checkins by another server of the same name
        redisService.withRedis {Jedis redis ->
            redis.hset("$SCHEDULER_PREFIX:checkIn", hostName, checkInDate.millis.toString())
        }
    }

    void cleanUpStaleServers() {
        redisService.withRedis {Jedis redis ->
            def now = new DateTime()
            def serverCheckInHash = redis.hgetAll("$SCHEDULER_PREFIX:checkIn")
            def staleServerHash = serverCheckInHash.findAll{ Seconds.secondsBetween(new DateTime(it.value as Long), now).seconds > STALE_SERVER_SECONDS }

            staleServerHash.each{ hostName, lastCheckInTimeMillis ->
                log.info "Cleaning up stale server $hostName"
                def staleServerAcquiredJobsSetName = getAcquiredIndexByHostName(hostName)

                def staleJobNames = redis.smembers(staleServerAcquiredJobsSetName)
                def triggerFireTime = staleJobNames.collect{ [(it):redis.hget("$TRIGGER_PREFIX:$it", TriggerField.NextFireTime.name)] }.sum()

                redis.watch(staleServerAcquiredJobsSetName)

                def transaction = redis.multi()

                staleJobNames.each{ jobName ->
                    transaction.hset("$TRIGGER_PREFIX:$jobName", TriggerField.State.name, TriggerState.Waiting.name)
                    transaction.hset("$TRIGGER_PREFIX:$jobName", TriggerField.AcquiredBy.name, "")
                    transaction.zadd(TRIGGER_NEXTFIRE_INDEX, triggerFireTime[jobName] as Long, jobName)
                }

                transaction.del(staleServerAcquiredJobsSetName)
                transaction.hdel("$SCHEDULER_PREFIX:checkIn", hostName)
                transaction.exec()
            }
        }
    }

    protected getAcquiredIndexByHostName(String hostName) {
        "$TRIGGER_PREFIX:${TriggerField.State.name}:${TriggerState.Acquired.name}:$hostName"
    }
}
