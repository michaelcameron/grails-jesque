package grails.plugin.jesque

import org.joda.time.DateTime

class Trigger {
    public static final String REDIS_PREFIX = 'trigger'
    public static final String TRIGGER_NEXTFIRE_INDEX = 'trigger:nextFireTime:WAITING:sorted'

    String jobName
    DateTime nextFireTime
    TriggerState state
    String acquiredBy

    static Trigger fromRedisHash(Map<String, String> hash) {
        Trigger trigger = new Trigger()
        trigger.jobName = hash.jobName
        trigger.nextFireTime = new DateTime(hash.nextFireTime.toLong())
        trigger.state = TriggerState.findByName(hash.state)
        trigger.acquiredBy = hash.acquiredBy

        return trigger
    }

    Map<String, String> toRedisHash() {

        [jobName: jobName, nextFireTime:nextFireTime.millis.toString(), state:state.name, acquiredBy:acquiredBy]
    }

    String getRedisKey() {
        "$REDIS_PREFIX:$jobName"
    }

    static getRedisKeyForJobName(String jobName) {
        "$REDIS_PREFIX:$jobName"
    }

    static String getAcquiredIndexByHostName(String hostName) {
        "$REDIS_PREFIX:state:${TriggerState.Acquired.name}:$hostName"
    }
}
