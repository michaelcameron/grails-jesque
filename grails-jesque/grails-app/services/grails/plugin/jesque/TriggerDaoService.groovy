package grails.plugin.jesque

import redis.clients.jedis.Jedis

class TriggerDaoService {

    static transactional = false

    def redisService

    void save( Trigger trigger) {
        redisService.withRedis { Jedis redis ->
            save( redis, trigger )
        }
    }

    void save( Jedis redis, Trigger trigger) {
        redis.hmset(trigger.redisKey, trigger.toRedisHash())
    }

    void delete( String jobName ) {
        redisService.withRedis { Jedis redis ->
            delete( redis, jobName )
        }
    }

    void delete( Jedis redis, String jobName ) {
        redis.del(Trigger.getRedisKeyForJobName(jobName))
        redis.zrem(Trigger.TRIGGER_NEXTFIRE_INDEX, jobName)
    }

    Trigger findByJobName(String jobName) {
        redisService.withRedis { Jedis redis ->
            findByJobName(redis, jobName)
        } as Trigger
    }

    Trigger findByJobName(Jedis redis, String jobName) {
        Trigger.fromRedisHash( redis.hgetAll( Trigger.getRedisKeyForJobName(jobName) ) )
    }

}
