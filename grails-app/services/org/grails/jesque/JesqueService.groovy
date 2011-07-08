package org.grails.jesque

import net.greghaines.jesque.client.Client
import net.greghaines.jesque.Job
import net.greghaines.jesque.worker.WorkerImpl

class JesqueService {

    static transactional = false

    def redisService
    def jesqueConfig
    Client jesqueClient

    //todo: other interface that does not require job
    def enqueue(String queue, Job job) {
        jesqueClient.enqueue(queue, job)
    }

    //todo: do not require worker actions to implement runnable
    //todo: do spring autowiring of actions before execution
    //todo: test that hibernate/gorm works correctly on worker threads
    def startWorker(queues, jobTypes) {

        def worker = new WorkerImpl(jesqueConfig, queues, jobTypes )
        def workerThread = new Thread(worker)
        workerThread.start()
    }
}
