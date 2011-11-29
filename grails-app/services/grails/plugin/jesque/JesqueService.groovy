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

    void enqueue(String queueName, String jobName, List args) {
        jesqueClient.enqueue(queueName, new Job(jobName, args))
    }

    void enqueue(String queueName, String jobName, Object... args) {
        jesqueClient.enqueue(queueName, new Job(jobName, args))
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

        def worker = new GrailsWorkerImpl(grailsApplication, jesqueConfig, queues, jobTypes)
        def listener = new WorkerHibernateListener(sessionFactory)
        worker.addListener(listener, WorkerEvent.JOB_EXECUTE, WorkerEvent.JOB_SUCCESS, WorkerEvent.JOB_FAILURE )
        def workerThread = new Thread(worker)
        workerThread.start()

        worker
    }

    void withWorker(String queueName, String jobName, Class jobClassName, Closure closure) {
        def worker = startWorker(queueName, jobName, jobClassName)
        try {
            closure()
        } finally {
            worker.end(true)
        }
    }

    void startWorkersFromConfig(Map jesqueConfigMap) {
        jesqueConfigMap?.workers?.each{ key, value ->
            log.info "Starting workers for pool $key"

            def workers = value.workers ? value.workers.toInteger() : 1

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
                def argumentString = parameterTypes.collect{ type -> "${argNumber++}" }.join(',')
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
