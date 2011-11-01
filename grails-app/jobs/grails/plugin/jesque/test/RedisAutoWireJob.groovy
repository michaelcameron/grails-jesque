package grails.plugin.jesque.test

/**
 */
class RedisAutoWireJob {

    def redisService

    void perform() {
//        assert redisService != null
        redisService.worked = "true"
        println "hello world"
    }
}
