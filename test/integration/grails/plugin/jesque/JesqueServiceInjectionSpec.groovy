package grails.plugin.jesque

import grails.plugin.jesque.test.RedisAutoWireJob
import grails.plugin.redis.RedisService
import grails.plugin.spock.IntegrationSpec
import net.greghaines.jesque.meta.dao.FailureDAO
import net.greghaines.jesque.meta.dao.QueueInfoDAO

/**
 */
class JesqueServiceInjectionSpec extends IntegrationSpec {

    JesqueService jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao
    RedisService redisService

    def setup() {
        redisService.flushDB()
    }

    void "test autowirejob with redis service injection with worker"() {
        given:
        def queueName = 'redisAutoWireJob'
        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count
        redisService.hello = "world"

        when:
        jesqueService.enqueue(queueName, RedisAutoWireJob.simpleName)
        jesqueService.withWorker(queueName, RedisAutoWireJob.simpleName, RedisAutoWireJob) {
            sleep(2000)
        }

        then:
        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
        assert redisService.hello == "world"
        assert redisService.worked == "true"
    }

    void "test autowirejob with redis service injection via config"() {
        given:
        def queueName = 'redisAutoWireJob'
        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count
        redisService.hello = "world"

        when:
        jesqueService.enqueue(queueName, RedisAutoWireJob.simpleName)

        then:
        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
        assert redisService.hello == "world"
        assert redisService.worked == "true"
    }
}
