package grails.plugin.jesque

import net.greghaines.jesque.client.Client
import net.greghaines.jesque.Job

import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.meta.dao.WorkerInfoDAO
import net.greghaines.jesque.meta.WorkerInfo

class JesqueService {

    static transactional = false

    def grailsApplication
    def sessionFactory
    def jesqueConfig
    Client jesqueClient
    WorkerInfoDAO workerInfoDao

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

    void startWorkersFromConfig() {
        def jesqueConfigMap = grailsApplication.config?.grails?.jesque ?: [:]
        jesqueConfigMap?.workers?.each{ key, value ->
            startWorker(value.queue, value.jobTypes)
        }
    }

    void pruneWorkers() {
        def hostName = InetAddress.localHost.hostName
        workerInfoDao.allWorkers?.each { WorkerInfo workerInfo ->
            if( workerInfo.host == hostName ) {
                log.debug "Removing stale worker $workerInfo.name"
                workerInfoDao.removeWorker(workerInfo.name)
            }
        }
    }
}
