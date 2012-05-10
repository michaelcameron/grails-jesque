package grails.plugin.jesque

import redis.clients.jedis.Jedis
import grails.converters.JSON
import redis.clients.jedis.Transaction

import redis.clients.jedis.Tuple
import org.joda.time.DateTime
import net.greghaines.jesque.Job
import net.greghaines.jesque.json.ObjectMapperFactory
import org.joda.time.Seconds
import org.springframework.scheduling.annotation.Scheduled
import org.joda.time.DateTimeZone

//TODO: failure between multi and exec may lead to redis connection to be put back into pool still in multi
class JesqueSchedulerService {
    static transactional = false

    def redisService
    def jesqueService
    def grailsApplication
    def scheduledJobDaoService
    def triggerDaoService

    protected static final String TRIGGER_PREFIX = 'trigger'
    protected static final String SCHEDULER_PREFIX = 'scheduler'
    protected static final String RESQUE_QUEUES_INDEX = 'resque:queues'
    protected static final String RESQUE_QUEUE_PREFIX = 'resque:queue'

    protected static final Integer STALE_SERVER_SECONDS = 30

    void schedule(String jobName, String cronExpressionString, String jesqueJobQueue, String jesqueJobName, Object... args) {
        schedule(jobName, cronExpressionString, jesqueJobQueue, jesqueJobName, args.toList())
    }

    void schedule(String jobName, String cronExpressionString, String jesqueJobQueue, String jesqueJobName, List args) {
        schedule(jobName, cronExpressionString, DateTimeZone.default, jesqueJobQueue, jesqueJobName, args)
    }
    
    void schedule(String jobName, String cronExpressionString, DateTimeZone timeZone, String jesqueJobQueue, String jesqueJobName, Object... args) {
        schedule(jobName, cronExpressionString, timeZone, jesqueJobQueue, jesqueJobName, args.toList())
    }

    void schedule(String jobName, String cronExpressionString, DateTimeZone timeZone, String jesqueJobQueue, String jesqueJobName, List args) {
        assert CronExpression.isValidExpression(cronExpressionString)
        CronExpression cronExpression = new CronExpression(cronExpressionString, timeZone)

        redisService.withRedis{ Jedis redis ->
            //add schedule
            ScheduledJob scheduledJob = new ScheduledJob()
            scheduledJob.name = jobName
            scheduledJob.cronExpression = cronExpressionString
            scheduledJob.args = args
            scheduledJob.jesqueJobName = jesqueJobName
            scheduledJob.jesqueJobQueue = jesqueJobQueue
            scheduledJob.timeZone = timeZone
            scheduledJobDaoService.save(redis, scheduledJob)

            //add trigger state
            Trigger trigger = new Trigger()
            trigger.jobName = jobName
            trigger.nextFireTime = cronExpression.getNextValidTimeAfter(new DateTime())
            trigger.state = TriggerState.Waiting
            trigger.acquiredBy = ''
            triggerDaoService.save(redis, trigger)

            //update trigger indexes
            redis.zadd(Trigger.TRIGGER_NEXTFIRE_INDEX, trigger.nextFireTime.millis, jobName)
        }
    }

    void deleteSchedule(String name) {
        scheduledJobDaoService.delete(name)
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
            log.debug "Waiting for fire time to enqueue jobs"
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
            Set<Tuple> waitingJobs = redis.zrangeWithScores(Trigger.TRIGGER_NEXTFIRE_INDEX, 0, number - 1)
            waitingJobs = waitingJobs.findAll{ it.score <= until.millis }

            if( !waitingJobs )
                return []

            //only get and wait for jobs with the exact time until something like quartz batchTimeWindow is implemented
            def earliestWaitingJobTime = waitingJobs.min{ it.score }.score
            waitingJobs.findAll{ it.score == earliestWaitingJobTime }

            //lock jobs
            waitingJobs.inject([]){ List<Tuple> acquiredJobs, Tuple jobData ->
                String jobName = jobData.element

                redis.watch(Trigger.getRedisKeyForJobName(jobName))
                Trigger trigger = triggerDaoService.findByJobName(jobName)
                if( trigger.state != TriggerState.Waiting ) {
                    log.debug "Trigger not in waiting state for job ${jobName}"
                    redis.unwatch()
                    return acquiredJobs
                }

                Transaction transaction = redis.multi()
                trigger.state = TriggerState.Acquired
                trigger.acquiredBy = hostName
                transaction.hmset(trigger.redisKey, trigger.toRedisHash())
                transaction.zrem(Trigger.TRIGGER_NEXTFIRE_INDEX, jobName)
                transaction.sadd(Trigger.getAcquiredIndexByHostName(hostName), jobName)

                def transactionResult = transaction.exec()
                if( transactionResult != null )
                    acquiredJobs << jobData
                else
                    log.debug "Could not acquire job ${jobName} due to trigger state change"

                return acquiredJobs
            } as List<Tuple>
        } as List<Tuple>
    }

    void enqueueJob(Jedis redis, String jobName, String hostName) {
        log.info "Enqueuing job $jobName"
        redis.watch(Trigger.getRedisKeyForJobName(jobName))

        ScheduledJob scheduledJob = scheduledJobDaoService.findByName(redis, jobName)

        Trigger trigger = scheduledJob.trigger
        if( trigger.state != TriggerState.Acquired ) {
            log.warn "Trigger state is no longer ${TriggerState.Acquired.name} for job $jobName"
            redis.srem(Trigger.getAcquiredIndexByHostName(hostName), jobName)
            redis.unwatch()
            return
        }
        if( trigger.acquiredBy != hostName ) {
            log.warn "Trigger state was acquired by another server ${trigger.acquiredBy} for job $jobName"
            redis.srem(Trigger.getAcquiredIndexByHostName(hostName), jobName)
            redis.unwatch()
            return
        }
        def cronExpression = new CronExpression(scheduledJob.cronExpression, scheduledJob.timeZone)
        trigger.nextFireTime = cronExpression.getNextValidTimeAfter(new DateTime())
        trigger.state = TriggerState.Waiting
        trigger.acquiredBy = ""

        def job = new Job(scheduledJob.jesqueJobName, scheduledJob.args)
        String jobJson = ObjectMapperFactory.get().writeValueAsString(job)

        def transaction = redis.multi()
        transaction.sadd(RESQUE_QUEUES_INDEX, scheduledJob.jesqueJobQueue)
        transaction.rpush(getRedisKeyForQueueName(scheduledJob.jesqueJobQueue), jobJson)
        transaction.hmset(trigger.redisKey, trigger.toRedisHash())
        transaction.zadd(Trigger.TRIGGER_NEXTFIRE_INDEX, trigger.nextFireTime.millis, jobName)
        if( transaction.exec() == null )
            log.warn "Job exection aborted for $jobName because the trigger data changed"

        //always remove index regardless of the result of the enqueue
        redis.srem(Trigger.getAcquiredIndexByHostName(hostName), jobName)
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
                def staleServerAcquiredJobsSetName = Trigger.getAcquiredIndexByHostName(hostName)

                def staleJobNames = redis.smembers(staleServerAcquiredJobsSetName)
                def triggerFireTime = staleJobNames.collect{ [(it):redis.hget("$TRIGGER_PREFIX:$it", TriggerField.NextFireTime.name)] }.sum()

                redis.watch(staleServerAcquiredJobsSetName)

                def transaction = redis.multi()

                staleJobNames.each{ jobName ->
                    transaction.hset("$TRIGGER_PREFIX:$jobName", TriggerField.State.name, TriggerState.Waiting.name)
                    transaction.hset("$TRIGGER_PREFIX:$jobName", TriggerField.AcquiredBy.name, "")
                    transaction.zadd(Trigger.TRIGGER_NEXTFIRE_INDEX, triggerFireTime[jobName] as Long, jobName)
                }

                transaction.del(staleServerAcquiredJobsSetName)
                transaction.hdel("$SCHEDULER_PREFIX:checkIn", hostName)
                transaction.exec()
            }
        }
    }

    public String getRedisKeyForQueueName(String queueName) {
        "$RESQUE_QUEUE_PREFIX:${queueName}"
    }
}
