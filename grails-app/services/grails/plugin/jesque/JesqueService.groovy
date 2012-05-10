package grails.plugin.jesque

import net.greghaines.jesque.client.Client
import net.greghaines.jesque.Job

import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.meta.dao.WorkerInfoDAO
import net.greghaines.jesque.meta.WorkerInfo
import java.lang.reflect.Method
import grails.plugin.jesque.annotation.Async
import org.codehaus.groovy.grails.lifecycle.ShutdownOperations

class JesqueService {

    static transactional = false
    static scope = 'singleton'

    static final int DEFAULT_WORKER_POOL_SIZE = 3

    def grailsApplication
    def sessionFactory
    def jesqueConfig
    Client jesqueClient
    WorkerInfoDAO workerInfoDao
    List<Worker> workers = Collections.synchronizedList([])

    JesqueService() {
        ShutdownOperations.addOperation({ this.stopAllWorkers() })
    }

    void enqueue(String queueName, Job job) {
        jesqueClient.enqueue(queueName, job)
    }

    void enqueue(String queueName, Class jobClazz, List args) {
        enqueue(queueName, jobClazz.name, args)
    }

    void enqueue(String queueName, String jobName, List args) {
        jesqueClient.enqueue(queueName, new Job(jobName, args))
    }

    void enqueue(String queueName, String jobName, Object... args) {
        jesqueClient.enqueue(queueName, new Job(jobName, args))
    }

    void enqueue(String queueName, Class jobClazz, Object... args) {
        enqueue(queueName, jobClazz.name, args)
    }

    Worker startWorker(String queueName, String jobName, Class jobClass) {
        startWorker([queueName], [(jobName):jobClass])
    }

    Worker startWorker(List queueName, String jobName, Class jobClass) {
        startWorker(queueName, [(jobName):jobClass])
    }

    Worker startWorker(String queueName, Map<String, Class> jobTypes) {
        startWorker([queueName], jobTypes)
    }

    Worker startWorker(List<String> queues, Map<String, Class> jobTypes) {
        log.debug "Starting worker processing queueus: ${queues}"
        def worker = new GrailsWorkerImpl(grailsApplication, jesqueConfig, queues, jobTypes)
        workers.add(worker)

        def workerHibernateListener = new WorkerHibernateListener(sessionFactory)
        worker.addListener(workerHibernateListener, WorkerEvent.JOB_EXECUTE, WorkerEvent.JOB_SUCCESS, WorkerEvent.JOB_FAILURE)

        def workerLifeCycleListener = new WorkerLifecycleListener(this)
        worker.addListener(workerLifeCycleListener, WorkerEvent.WORKER_STOP)

        def workerThread = new Thread(worker)
        workerThread.start()

        worker
    }

    void stopAllWorkers() {
        log.debug "Stopping ${workers.size()} jesque workers"

        List<Worker> workersToRemove = workers.collect{ it }
        workersToRemove.each { Worker worker ->
            try{
                log.debug "Stopping worker processing queues: ${worker.queues}"
                worker.end(true)
                worker.join(5000)
            } catch(Exception exception) {
                log.error "Exception ending jesque worker", exception
            }
        }
    }

    void withWorker(String queueName, String jobName, Class jobClassName, Closure closure) {
        def worker = startWorker(queueName, jobName, jobClassName)
        try {
            closure()
        } finally {
            worker.end(true)
        }
    }

    void startWorkersFromConfig(ConfigObject jesqueConfigMap) {
        jesqueConfigMap.workers.each{ key, value ->
            log.info "Starting workers for pool $key"

            def workers = value.workers ? value.workers.toInteger() : DEFAULT_WORKER_POOL_SIZE

            workers.times {
                startWorker(value.queueNames, value.jobTypes)
            }
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

    public void removeWorkerFromLifecycleTracking(Worker worker) {
        log.debug "Removing worker ${worker.name} from lifecycle tracking"
        workers.remove(worker)
    }
}
