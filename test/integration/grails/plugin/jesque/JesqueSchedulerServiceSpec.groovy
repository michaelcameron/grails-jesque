package grails.plugin.jesque

import grails.test.spock.IntegrationSpec
import org.joda.time.DateTimeZone
import org.joda.time.DateTime
import redis.clients.jedis.Jedis
import redis.clients.jedis.Tuple

class JesqueSchedulerServiceSpec extends IntegrationSpec {

    def jesqueSchedulerService
    def scheduledJobDaoService
    def triggerDaoService
    def redisService

    void setup() {
        redisService.flushDB()
    }

    void tearDown() {
        redisService.flushDB()
    }

    void "test schedule"() {
        given:
        def jobName = 'new job'
        def cronExpression = '0/10 * * * * ?'
        def timeZone = DateTimeZone.UTC
        def jesqueJobName = 'jobName'
        def jesqueJobQueue = 'queue'
        def now = new DateTime()

        when:
        jesqueSchedulerService.schedule(jobName, cronExpression, timeZone, jesqueJobQueue, jesqueJobName, 1, "two")

        then:
        ScheduledJob scheduledJob = scheduledJobDaoService.findByName(jobName)
        scheduledJob != null
        scheduledJob.name == jobName
        scheduledJob.cronExpression == cronExpression
        scheduledJob.timeZone == timeZone
        scheduledJob.jesqueJobName == jesqueJobName
        scheduledJob.jesqueJobQueue == jesqueJobQueue
        scheduledJob.args == [1, "two"]

        scheduledJob.trigger != null
        scheduledJob.trigger.jobName == jobName
        scheduledJob.trigger.acquiredBy == ''
        scheduledJob.trigger.nextFireTime > now
        scheduledJob.trigger.state == TriggerState.Waiting

        Set jobs = redisService.withRedis { Jedis redis ->
            redis.smembers(ScheduledJob.JOB_INDEX)
        } as Set
        jobs.size() == 1
        jobs.contains(jobName)

        scheduledJobDaoService.all.size() == 1
        scheduledJobDaoService.all[0].name == jobName

        Set<Tuple> triggerIndex = redisService.withRedis { Jedis redis ->
            redis.zrangeWithScores(Trigger.TRIGGER_NEXTFIRE_INDEX, 0, -1)
        } as Set<Tuple>
        triggerIndex.size() == 1
        triggerIndex.toList()[0].score.toLong() == scheduledJob.trigger.nextFireTime.millis
        triggerIndex.toList()[0].element == jobName
    }

    void "test acquireJob"() {
        given:
        def hostname = 'hostname'
        def jobName = 'new job'
        def cronExpression = '* * * * * ?'
        def timeZone = DateTimeZone.UTC
        def jesqueJobName = 'jobName'
        def jesqueJobQueue = 'queue'
        jesqueSchedulerService.schedule(jobName, cronExpression, timeZone, jesqueJobQueue, jesqueJobName, 1, "two")
        def scheduledJob = scheduledJobDaoService.findByName(jobName)

        when:
        def acquiredJobs = jesqueSchedulerService.acquireJobs(scheduledJob.trigger.nextFireTime, 1, hostname)

        then:
        acquiredJobs != null
        acquiredJobs.size() == 1
        acquiredJobs[0].score.toLong() == scheduledJob.trigger.nextFireTime.millis
        acquiredJobs[0].element == scheduledJob.name
        def trigger = triggerDaoService.findByJobName(jobName)
        trigger != null
        trigger.state == TriggerState.Acquired
        trigger.acquiredBy == hostname

        Set<Tuple> triggerIndex = redisService.withRedis { Jedis redis ->
            redis.zrangeWithScores(Trigger.TRIGGER_NEXTFIRE_INDEX, 0, -1)
        } as Set<Tuple>
        triggerIndex.size() == 0

        Set<String> acquiredIndex = redisService.withRedis { Jedis redis ->
            redis.smembers(Trigger.getAcquiredIndexByHostName(hostname))
        } as Set<Tuple>
        acquiredIndex.size() == 1
        acquiredIndex.contains(jobName)
    }

    void "test acquireJob when the trigger changes (but job is still in index of acquriable jobs)"() {
        given:
        def hostname = 'hostname'
        def jobName = 'new job'
        def cronExpression = '* * * * * ?'
        def timeZone = DateTimeZone.UTC
        def jesqueJobName = 'jobName'
        def jesqueJobQueue = 'queue'
        jesqueSchedulerService.schedule(jobName, cronExpression, timeZone, jesqueJobQueue, jesqueJobName, 1, "two")
        def scheduledJob = scheduledJobDaoService.findByName(jobName)
        scheduledJob.trigger.state = TriggerState.Acquired
        triggerDaoService.save(scheduledJob.trigger)

        when:
        def acquiredJobs = jesqueSchedulerService.acquireJobs(scheduledJob.trigger.nextFireTime, 1, hostname)

        then:
        acquiredJobs != null
        acquiredJobs.size() == 0

        Set<String> acquiredIndex = redisService.withRedis { Jedis redis ->
            redis.smembers(Trigger.getAcquiredIndexByHostName(hostname))
        } as Set<Tuple>
        acquiredIndex.size() == 0
    }

    void "test enqueueJob"() {
        given:
        def now = new DateTime()
        def hostname = 'hostname'
        def jobName = 'new job'
        def cronExpression = '* * * * * ?'
        def timeZone = DateTimeZone.UTC
        def jesqueJobName = 'jobName'
        def jesqueJobQueue = 'queue'
        jesqueSchedulerService.schedule(jobName, cronExpression, timeZone, jesqueJobQueue, jesqueJobName, 1, "two")
        def scheduledJob = scheduledJobDaoService.findByName(jobName)

        when:
        def acquiredJobs = jesqueSchedulerService.acquireJobs(scheduledJob.trigger.nextFireTime, 1, hostname)

        then:
        acquiredJobs != null
        acquiredJobs.size() == 1
        acquiredJobs[0].element == jobName

        when:
        redisService.withRedis { Jedis redis ->
            jesqueSchedulerService.enqueueJob(redis, acquiredJobs[0].element, hostname)
        }

        then:
        def trigger = triggerDaoService.findByJobName(jobName)
        trigger.state == TriggerState.Waiting

        def resqueQueues = redisService.withRedis { Jedis redis -> redis.smembers(JesqueSchedulerService.RESQUE_QUEUES_INDEX) }
        resqueQueues.size() == 1
        resqueQueues.contains( jesqueJobQueue)

        def resqueJobQueueSize = redisService.withRedis { Jedis redis -> redis.llen(jesqueSchedulerService.getRedisKeyForQueueName(jesqueJobQueue)) }
        resqueJobQueueSize == 1

        Set<Tuple> triggerIndex = redisService.withRedis { Jedis redis ->
            redis.zrangeWithScores(Trigger.TRIGGER_NEXTFIRE_INDEX, 0, -1)
        } as Set<Tuple>
        triggerIndex.size() == 1
        triggerIndex.toList()[0].score.toLong() > now.millis
        triggerIndex.toList()[0].element == jobName

        Set<String> acquiredIndex = redisService.withRedis { Jedis redis ->
            redis.smembers(Trigger.getAcquiredIndexByHostName(hostname))
        } as Set<Tuple>
        acquiredIndex.size() == 0
    }
}
