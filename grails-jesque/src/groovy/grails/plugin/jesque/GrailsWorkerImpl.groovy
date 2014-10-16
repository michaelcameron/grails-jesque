package grails.plugin.jesque

import grails.spring.BeanBuilder
import org.codehaus.groovy.grails.commons.GrailsApplication
import static net.greghaines.jesque.utils.ResqueConstants.WORKER
import static net.greghaines.jesque.worker.WorkerEvent.JOB_PROCESS
import static net.greghaines.jesque.worker.WorkerEvent.JOB_EXECUTE
import net.greghaines.jesque.Config
import net.greghaines.jesque.Job
import net.greghaines.jesque.worker.UnpermittedJobException
import net.greghaines.jesque.worker.WorkerImpl
import net.greghaines.jesque.worker.RecoveryStrategy
import static net.greghaines.jesque.worker.WorkerEvent.WORKER_ERROR
import redis.clients.jedis.exceptions.JedisConnectionException

class GrailsWorkerImpl extends WorkerImpl {

    private Log
    BeanBuilder beanBuilder
    GrailsApplication grailsApplication
    JobExceptionHandler jobExceptionHandler

    public GrailsWorkerImpl(
            GrailsApplication grailsApplication,
            final Config config,
            final Collection<String> queues,
            final Map<String, Class> jobTypes) {
        super(config, queues, jobTypes)

        this.grailsApplication = grailsApplication
        beanBuilder = new BeanBuilder()
    }

    protected void checkJobType(final String jobName, final Class<?> jobType) {
        if (jobName == null) {
            throw new IllegalArgumentException("jobName must not be null")
        }
        if (jobType == null) {
            throw new IllegalArgumentException("jobType must not be null")
        }
    }

    protected void process(final Job job, final String curQueue) {
        this.listenerDelegate.fireEvent(JOB_PROCESS, this, curQueue, job, null, null, null)
        renameThread("Processing " + curQueue + " since " + System.currentTimeMillis())
        try {
            Class jobClass = jobTypes[job.className]
            if(!jobClass) {
                throw new UnpermittedJobException(job.className)
            }
            def instance = createInstance(jobClass.canonicalName)
            execute(job, curQueue, instance, job.args)
        } catch (Exception e) {
            failure(e, job, curQueue)
        }
    }

    protected void failure(final Exception ex, final Job job, final String curQueue) {
        jobExceptionHandler?.onException(ex, job, curQueue)
        super.failure(ex, job, curQueue)
    }

    protected Object createInstance(String fullClassName) {
        grailsApplication.mainContext.getBean(fullClassName)
    }

    protected void execute(final Job job, final String curQueue, final Object instance, final Object[] args) {
        this.jedis.set(key(WORKER, this.name), statusMsg(curQueue, job))
        try {
            final Object result
            this.listenerDelegate.fireEvent(JOB_EXECUTE, this, curQueue, job, instance, null, null)
            result = instance.perform(* args)
            success(job, instance, result, curQueue)
        } finally {
            this.jedis.del(key(WORKER, this.name))
        }
    }

    protected void recoverFromException(final String curQueue, final Exception e) {
        super.recoverFromException(curQueue, e)
        final RecoveryStrategy recoveryStrategy = this.exceptionHandler.onException(this, e, curQueue)
        final int reconnectAttempts = getReconnectAttempts()
        switch (recoveryStrategy)
        {
            case RecoveryStrategy.RECONNECT:
                def attempt = 0

                while(attempt++ <= reconnectAttempts && !this.jedis.isConnected()) {
                    log.info("Reconnecting to Redis in response to exception - Attempt $attempt of $reconnectAttempts", e)
                    try
                    {
                        this.jedis.disconnect()
                        try { Thread.sleep(reconnectSleepTime) } catch (Exception ignore){}
                        this.jedis.connect()
                        def pingResult = this.jedis.ping()
                        if( pingResult != "PONG" )
                            log.info("Unexpected redis ping result, $pingResult")
                    } catch (JedisConnectionException ignore) {
                        // Ignore bad connection attempts
                    } catch (Exception exception) {
                        log.error("Recived exception when trying to reconnect to Redis", exception)
                        throw exception
                    }
                }
                if (!this.jedis.isConnected()) {
                    log.error("Terminating in response to exception after $reconnectAttempts to reconnect", e)
                    end(false)
                } else {
                    log.info("Reconnected to Redis after $attempt attempts")
                }
                break
            case RecoveryStrategy.TERMINATE:
                log.error("Terminating in response to exception", e)
                end(false)
                break
            case RecoveryStrategy.PROCEED:
                this.listenerDelegate.fireEvent(WORKER_ERROR, this, curQueue, null, null, null, e)
                break
            default:
                log.error("Unknown WorkerRecoveryStrategy: $recoveryStrategy while attempting to recover from the following exception; worker proceeding...", e)
                break
        }
    }
}
