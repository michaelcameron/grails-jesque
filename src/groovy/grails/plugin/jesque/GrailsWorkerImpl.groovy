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

class GrailsWorkerImpl extends WorkerImpl {

    BeanBuilder beanBuilder
    GrailsApplication grailsApplication

    public GrailsWorkerImpl(
            GrailsApplication grailsApplication,
            final Config config,
            final Collection<String> queues,
            final Map<String, Class> jobTypes) {
        super(config, queues, jobTypes)

        this.grailsApplication = grailsApplication
        beanBuilder = new BeanBuilder()
    }

    protected void checkJobTypes(final Map<String,? extends Class<?>> jobTypes) {
        if (jobTypes == null) {
            throw new IllegalArgumentException("jobTypes must not be null")
        }
        if (jobTypes.any{ key, value -> key == null || value == null }) {
            throw new IllegalArgumentException("jobType's keys and values must not be null: " + jobTypes)
        }
    }

    public void addJobType(final String jobName, final Class<?> jobType) {
        if (jobName == null) {
            throw new IllegalArgumentException("jobName must not be null")
        }
        if (jobType == null) {
            throw new IllegalArgumentException("jobType must not be null")
        }

        this.jobTypes.put( jobName, jobType )
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
            execute(job, curQueue, instance, job.args);
        } catch (Exception e) {
            failure(e, job, curQueue);
        }
    }

    protected Object createInstance(String fullClassName) {
        grailsApplication.mainContext.getBean(fullClassName)
    }

    protected void execute(final Job job, final String curQueue, final Object instance, final Object[] args) {
        this.jedis.set(key(WORKER, this.name), statusMsg(curQueue, job));
        try {
            final Object result
            this.listenerDelegate.fireEvent(JOB_EXECUTE, this, curQueue, job, instance, null, null);
            result = instance.perform(* args)
            success(job, instance, result, curQueue);
        } finally {
            this.jedis.del(key(WORKER, this.name));
        }
    }
}
