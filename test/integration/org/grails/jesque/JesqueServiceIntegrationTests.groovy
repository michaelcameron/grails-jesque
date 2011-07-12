package org.grails.jesque

import org.grails.jesque.test.SimpleJob
import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import org.grails.jesque.test.Foo
import org.grails.jesque.test.DomainJob
import org.grails.jesque.test.AutoWireJob

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
            sleep(5000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }

    void testWorkerDomainJob() {
        def queueName = 'testQueue'

        def fooName = 'name'
        def foo = Foo.build(name:fooName)

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, DomainJob, foo.id )
        jesqueService.withWorker(queueName, DomainJob) {
            sleep(5000)
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
            sleep(5000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }
}
