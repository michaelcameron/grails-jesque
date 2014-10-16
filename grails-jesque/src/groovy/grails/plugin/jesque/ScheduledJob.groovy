package grails.plugin.jesque

import grails.converters.JSON
import org.joda.time.DateTimeZone

class ScheduledJob {
    public static final String REDIS_PREFIX = 'job'
    public static final String JOB_INDEX = 'job:all'

    String name
    String cronExpression
    DateTimeZone timeZone
    List args
    String jesqueJobName
    String jesqueJobQueue

    Trigger trigger

    static ScheduledJob fromRedisHash(Map<String, String> hash) {
        ScheduledJob job = new ScheduledJob()
        job.cronExpression = hash.cronExpression
        job.args = JSON.parse(hash.args) as List
        job.jesqueJobName = hash.jesqueJobName
        job.jesqueJobQueue = hash.jesqueJobQueue
        job.name = hash.name
        job.timeZone = DateTimeZone.forID(hash.timeZone)

        return job
    }

    Map<String, String> toRedisHash() {
        String argsJson = new JSON(args).toString()

        [name: name, cronExpression:cronExpression, args:argsJson, jesqueJobName:jesqueJobName, jesqueJobQueue:jesqueJobQueue, timeZone:timeZone.ID]
    }

    String getRedisKey() {
        "$REDIS_PREFIX:$name"
    }

    static getRedisKeyForName(String name) {
        "$REDIS_PREFIX:$name"
    }
}
