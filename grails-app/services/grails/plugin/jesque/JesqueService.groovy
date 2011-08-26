package grails.plugin.jesque

import net.greghaines.jesque.client.Client
import net.greghaines.jesque.Job

import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.meta.dao.WorkerInfoDAO
import net.greghaines.jesque.meta.WorkerInfo
import java.lang.reflect.Method
import grails.plugin.jesque.annotation.Async

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

    Worker startWorker(List queueName, Class jobType) {
        startWorker(queueName, [jobType])
    }

    Worker startWorker(String queueName, List jobType) {
        startWorker([queueName], jobType)
    }

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
            log.info "Starting workers for pool $key"

            def workers = value.workers ? value.workers.toInteger() : 1

            def queueNames = value.queueNames
            if( !((queueNames instanceof String) || (queueNames instanceof List<String>)) )
                throw new Exception("Invalid queueNames ($queueNames) for pool $key")

            def jobTypes = value.jobTypes
            if( !((jobTypes instanceof String) || (jobTypes instanceof Class) || (jobTypes instanceof List)) )
                throw new Exception("Invalid jobTypes ($jobTypes) for pool $key")

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

    void createAsyncMethods() {
        grailsApplication.jobClasses.each{ Class jobClass ->
            jobClass.methods.each{ Method method ->
                if( !(Async in method.annotations ) )
                    return

                if( method.returnType != Void )
                    throw new Exception("The return type of an async method must be void")

                /*
                def parameterTypes = method.parameterTypes
                def argNumber = 0
                def parameterString = parameterTypes.collect{ type -> "$type.name ${argNumber++}" }.join(',')
                argNumber = 0
                def argumentString = parameterTypes.colelct{ type -> "${argNumber++}" }.join(',')
                def closureString = "{"
                jobClass.metaClass."${method.name}Async" = Eval.me """
                { $parameterString ->

                }
                """
                */
            }
        }
    }
}
