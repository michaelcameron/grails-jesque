package test

class RedisAutoWireJob {

    def redisService

    void perform() {
        redisService.hello = "world"
        redisService.worked = "true"
        println "hello world"
    }
}
