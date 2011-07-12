package org.grails.jesque

import net.greghaines.jesque.client.Client
import net.greghaines.jesque.Job
import net.greghaines.jesque.worker.WorkerImpl
import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.worker.WorkerEvent

class JesqueService {

    static transactional = false

    def grailsApplication
    def sessionFactory
    def jesqueConfig
    Client jesqueClient

    void enqueue(String queueName, Job job) {
        jesqueClient.enqueue(queueName, job)
    }

    void enqueue(String queueName, Class jobClass, List args) {
        enqueue(queueName, jobClass?.name, args)
    }

    void enqueue(String queueName, String jobClassName, List args) {
        jesqueClient.enqueue(queueName, new Job(jobClassName, args))
    }

    void enqueue(String queueName, Class jobClass, Object... args) {
        enqueue(queueName, jobClass?.name, args)
    }

    void enqueue(String queueName, String jobClassName, Object... args) {
        jesqueClient.enqueue(queueName, new Job(jobClassName, args))
    }


    Worker startWorker(String queueName, Class jobType) {
        startWorker([queueName], [jobType])
    }

    //todo: do not require worker actions to implement runnable
    //todo: do spring autowiring of actions before execution
    //todo: test that hibernate/gorm works correctly on worker threads
    Worker startWorker(List<String> queues, List<Class> jobTypes) {

        def worker = new GrailsWorkerImpl(grailsApplication, jesqueConfig, queues, jobTypes )
        def listener = new WorkerHibernateListener(sessionFactory)
        worker.addListener(listener, WorkerEvent.JOB_EXECUTE, WorkerEvent.JOB_SUCCESS, WorkerEvent.JOB_FAILURE )
        def workerThread = new Thread(worker)
        workerThread.start()

        worker
    }

    void withWorker(String queueName, Class jobType, Closure closure) {
        def worker = startWorker(queueName, jobType)
        try {
            closure()
        } finally {
            worker.end(true)
        }
    }
}
