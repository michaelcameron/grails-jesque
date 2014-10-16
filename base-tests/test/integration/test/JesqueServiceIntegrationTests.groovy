package test

import grails.test.spock.IntegrationSpec
import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import grails.plugin.redis.RedisService

class JesqueServiceIntegrationTests extends IntegrationSpec {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao
    RedisService redisService

    void setup() {
        redisService.flushDB()
    }

    void tearDown() {
        redisService.flushDB()
    }

    void testWorkerSimpleJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, SimpleJob.simpleName )
        jesqueService.withWorker(queueName, SimpleJob.simpleName, SimpleJob) {
            sleep(2000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }

    void testWorkerDomainJob() {
        def queueName = 'domainJobTestQueue'

        def fooName = 'name'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, DomainJob.simpleName, fooName )
        jesqueService.withWorker(queueName, DomainJob.simpleName, DomainJob) {
            sleep(2000)
        }

        assert Foo.count() == 1

        assert existingFailureCount == failureDao.count
        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert fooName == Foo.list().first().name
    }

    void testAutoWireJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, AutoWireJob.simpleName )
        jesqueService.withWorker(queueName, AutoWireJob.simpleName, AutoWireJob ) {
            sleep(2000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }

    void testExceptionJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, ExceptionJob.simpleName )
        jesqueService.withWorker(queueName, ExceptionJob.simpleName, ExceptionJob ) {
            sleep(2000)
        }

        assert existingProcessedCount == queueInfoDao.processedCount
        assert existingFailureCount + 1 == failureDao.count
    }

    void testNonExistentJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, 'DoesNotExistJob' )
        jesqueService.withWorker(queueName, SimpleJob.simpleName, SimpleJob ) {
            sleep(2000)
        }

        assert existingProcessedCount == queueInfoDao.processedCount
        assert existingFailureCount + 1 == failureDao.count
    }

}
