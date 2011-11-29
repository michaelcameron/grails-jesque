package grails.plugin.jesque

import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import grails.plugin.jesque.test.Foo

import grails.plugin.jesque.test.ExceptionJob
import grails.plugin.jesque.test.DomainJob
import grails.plugin.jesque.test.SimpleJob
import grails.plugin.jesque.test.AutoWireJob
import grails.test.GrailsUnitTestCase
import grails.plugin.redis.RedisService

class JesqueServiceIntegrationTests extends GrailsUnitTestCase {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao
    RedisService redisService

    void setUp() {
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
        def queueName = 'testQueue'

        def fooName = 'name'
        def foo = new Foo(name:fooName)
        foo.save(failOnError:true)

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, DomainJob.simpleName, foo.id )
        jesqueService.withWorker(queueName, DomainJob.simpleName, DomainJob) {
            sleep(2000)
        }

        foo.refresh()

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
        assert fooName + fooName == foo.name
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
