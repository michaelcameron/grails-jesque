package grails.plugin.jesque

import grails.plugin.jesque.SimpleJob
import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import grails.plugin.jesque.test.Foo
import grails.plugin.jesque.DomainJob
import grails.plugin.jesque.AutoWireJob
import grails.plugin.jesque.ExceptionJob

class JesqueServiceIntegrationTests {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao

    void testWorkerSimpleJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, SimpleJob )
        jesqueService.withWorker(queueName, SimpleJob) {
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

        jesqueService.enqueue(queueName, DomainJob, foo.id )
        jesqueService.withWorker(queueName, DomainJob) {
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

        jesqueService.enqueue(queueName, AutoWireJob )
        jesqueService.withWorker(queueName, AutoWireJob ) {
            sleep(2000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }

    void testExceptionJob() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, ExceptionJob )
        jesqueService.withWorker(queueName, ExceptionJob ) {
            sleep(2000)
        }

        assert existingProcessedCount == queueInfoDao.processedCount
        assert existingFailureCount + 1 == failureDao.count
    }

}
