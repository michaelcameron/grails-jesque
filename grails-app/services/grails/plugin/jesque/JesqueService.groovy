package grails.plugin.jesque

import net.greghaines.jesque.Job
import net.greghaines.jesque.admin.Admin
import net.greghaines.jesque.admin.AdminClient
import net.greghaines.jesque.admin.AdminImpl
import net.greghaines.jesque.client.Client
import net.greghaines.jesque.meta.WorkerInfo
import net.greghaines.jesque.meta.dao.WorkerInfoDAO
import net.greghaines.jesque.worker.ExceptionHandler
import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.worker.WorkerListener
import org.joda.time.DateTime
import org.springframework.beans.factory.DisposableBean

class JesqueService implements DisposableBean {

    static transactional = false
    static scope = 'singleton'

    static final int DEFAULT_WORKER_POOL_SIZE = 3

    def grailsApplication
    def sessionFactory
    def jesqueConfig
    def jesqueDelayedJobService
    Client jesqueClient
    WorkerInfoDAO workerInfoDao
    List<Worker> workers = Collections.synchronizedList([])
    AdminClient jesqueAdminClient

    void enqueue(String queueName, Job job) {
        jesqueClient.enqueue(queueName, job)
    }

    void enqueue(String queueName, String jobName, List args) {
        enqueue(queueName, new Job(jobName, args))
    }

    void enqueue(String queueName, Class jobClazz, List args) {
        enqueue(queueName, jobClazz.simpleName, args)
    }

    void enqueue(String queueName, String jobName, Object... args) {
        enqueue(queueName, new Job(jobName, args))
    }

    void enqueue(String queueName, Class jobClazz, Object... args) {
        enqueue(queueName, jobClazz.simpleName, args)
    }

    void priorityEnqueue(String queueName, Job job) {
        jesqueClient.priorityEnqueue(queueName, job)
    }

    void priorityEnqueue(String queueName, String jobName, def args) {
        priorityEnqueue(queueName, new Job(jobName, args))
    }

    void priorityEnqueue(String queueName, Class jobClazz, def args) {
        priorityEnqueue(queueName, jobClazz.simpleName, args)
    }

    void enqueueAt(DateTime dateTime, String queueName, Job job) {
        jesqueDelayedJobService.enqueueAt(dateTime, queueName, job)
    }

    void enqueueAt(DateTime dateTime, String queueName, String jobName, Object... args) {
        enqueueAt( dateTime, queueName, new Job(jobName, args) )
    }

    void enqueueAt(DateTime dateTime, String queueName, Class jobClazz, Object... args) {
        enqueueAt( dateTime, queueName, jobClazz.simpleName, args)
    }

    void enqueueAt(DateTime dateTime, String queueName, String jobName, List args) {
        enqueueAt( dateTime, queueName, new Job(jobName, args) )
    }

    void enqueueAt(DateTime dateTime, String queueName, Class jobClazz, List args) {
        enqueueAt( dateTime, queueName, jobClazz.simpleName, args )
    }


    void enqueueIn(Integer millisecondDelay, String queueName, Job job) {
        enqueueAt( new DateTime().plusMillis(millisecondDelay), queueName, job )
    }

    void enqueueIn(Integer millisecondDelay, String queueName, String jobName, Object... args) {
        enqueueIn( millisecondDelay, queueName, new Job(jobName, args) )
    }

    void enqueueIn(Integer millisecondDelay, String queueName, Class jobClazz, Object... args) {
        enqueueIn( millisecondDelay, queueName, jobClazz.simpleName, args )
    }

    void enqueueIn(Integer millisecondDelay, String queueName, String jobName, List args) {
        enqueueIn( millisecondDelay, queueName, new Job(jobName, args) )
    }

    void enqueueIn(Integer millisecondDelay, String queueName, Class jobClazz, List args) {
        enqueueIn( millisecondDelay, queueName, jobClazz.simpleName, args )
    }


    Worker startWorker(String queueName, String jobName, Class jobClass, ExceptionHandler exceptionHandler = null,
                       boolean paused = false)
    {
        startWorker([queueName], [(jobName):jobClass], exceptionHandler, paused)
    }

    Worker startWorker(List queueName, String jobName, Class jobClass, ExceptionHandler exceptionHandler = null,
                       boolean paused = false)
    {
        startWorker(queueName, [(jobName):jobClass], exceptionHandler, paused)
    }

    Worker startWorker(String queueName, Map<String, Class> jobTypes, ExceptionHandler exceptionHandler = null,
                       boolean paused = false)
    {
        startWorker([queueName], jobTypes, exceptionHandler, paused)
    }

    Worker startWorker(List<String> queues, Map<String, Class> jobTypes, ExceptionHandler exceptionHandler = null,
                       boolean paused = false)
    {
        log.debug "Starting worker processing queueus: ${queues}"

        Class customWorkerClass = grailsApplication.config.grails.jesque.custom.worker.clazz
        Worker worker
        if (customWorkerClass && customWorkerClass in GrailsWorkerImpl) {
            worker = customWorkerClass.newInstance(grailsApplication, jesqueConfig, queues, jobTypes)
        } else {
            if (customWorkerClass)
                log.warn('The specified custom worker class does not extend GrailsWorkerImpl. Ignoring it')
            worker = new GrailsWorkerImpl(grailsApplication, jesqueConfig, queues, jobTypes)
        }

        Class customListenerClass = grailsApplication.config.grails.jesque.custom.listener.clazz
        if (customListenerClass && customListenerClass in WorkerListener) {
            worker.addListener(customListenerClass.newInstance() as WorkerListener)
        } else if (customListenerClass) {
            log.warn('The specified custom listener class does not implement WorkerListener. Ignoring it')
        }

        if (exceptionHandler)
            worker.exceptionHandler = exceptionHandler

        if (paused) {
            worker.togglePause(paused)
        }

        workers.add(worker)

        // create an Admin for this worker (makes it possible to administer across a cluster)
        Admin admin = new AdminImpl(jesqueConfig)
        admin.setWorker(worker)

        def workerHibernateListener = new WorkerHibernateListener(sessionFactory)
        worker.addListener(workerHibernateListener, WorkerEvent.JOB_EXECUTE, WorkerEvent.JOB_SUCCESS, WorkerEvent.JOB_FAILURE)

        def workerLifeCycleListener = new WorkerLifecycleListener(this)
        worker.addListener(workerLifeCycleListener, WorkerEvent.WORKER_STOP)

        def workerThread = new Thread(worker)
        workerThread.start()

        def adminThread = new Thread(admin)
        adminThread.start()

        worker
    }

    void stopAllWorkers() {
        log.info "Stopping ${workers.size()} jesque workers"

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

        def startPaused = jesqueConfigMap.startPaused as boolean ?: false

        jesqueConfigMap.workers.each { String workerPoolName, value ->
            log.info "Starting workers for pool $workerPoolName"

            def workers = value.workers ? value.workers.toInteger() : DEFAULT_WORKER_POOL_SIZE
            def queueNames = value.queueNames
            def jobTypes = value.jobTypes

            if (!((queueNames instanceof String) || (queueNames instanceof List<String>)))
                throw new Exception("Invalid queueNames for pool $workerPoolName, expecting must be a String or a List<String>.")

            if (!(jobTypes instanceof Map))
                throw new Exception("Invalid jobTypes (${jobTypes}) for pool $workerPoolName, must be a map")

            workers.times {
                startWorker(queueNames, jobTypes, null, startPaused)
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

    void destroy() throws Exception {
        this.stopAllWorkers()
    }

    void pauseAllWorkersOnThisNode() {
        log.info "Pausing all ${workers.size()} jesque workers on this node"

        List<Worker> workersToPause = workers.collect{ it }
        workersToPause.each { Worker worker ->
            log.debug "Pausing worker processing queues: ${worker.queues}"
            worker.togglePause(true)
        }
    }

    void resumeAllWorkersOnThisNode() {
        log.info "Resuming all ${workers.size()} jesque workers on this node"

        List<Worker> workersToPause = workers.collect{ it }
        workersToPause.each { Worker worker ->
            log.debug "Resuming worker processing queues: ${worker.queues}"
            worker.togglePause(false)
        }
    }

    void pauseAllWorkersInCluster() {
        log.debug "Pausing all workers in the cluster"
        jesqueAdminClient.togglePausedWorkers(true)
    }

    void resumeAllWorkersInCluster() {
        log.debug "Resuming all workers in the cluster"
        jesqueAdminClient.togglePausedWorkers(false)
    }

    void shutdownAllWorkersInCluster() {
        log.debug "Shutting down all workers in the cluster"
        jesqueAdminClient.shutdownWorkers(true)
    }

    boolean areAllWorkersInClusterPaused() {
        return workerInfoDao.getActiveWorkerCount() == 0
    }
}
