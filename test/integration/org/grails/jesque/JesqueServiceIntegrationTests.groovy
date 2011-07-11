package org.grails.jesque

import org.grails.jesque.test.SimpleAction
import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import org.grails.jesque.test.Foo
import org.grails.jesque.test.DomainAction

class JesqueServiceIntegrationTests {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao

    void testWorkerSimpleAction() {
        def queueName = 'testQueue'

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, SimpleAction )
        jesqueService.withWorker(queueName, SimpleAction) {
            sleep(5000)
        }

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
    }

    void testWorkerDomainAccess() {
        def queueName = 'testQueue'

        def fooName = 'name'
        def foo = Foo.build(name:fooName)

        def existingProcessedCount = queueInfoDao.processedCount
        def existingFailureCount = failureDao.count

        jesqueService.enqueue(queueName, DomainAction, foo.id )
        jesqueService.withWorker(queueName, DomainAction) {
            sleep(5000)
        }

        foo.refresh()

        assert existingProcessedCount + 1 == queueInfoDao.processedCount
        assert existingFailureCount == failureDao.count
        assert fooName + fooName == foo.name
    }
}
