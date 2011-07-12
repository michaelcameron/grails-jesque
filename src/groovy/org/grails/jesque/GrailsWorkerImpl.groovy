package org.grails.jesque

import net.greghaines.jesque.worker.WorkerImpl
import net.greghaines.jesque.Config
import grails.spring.BeanBuilder
import org.codehaus.groovy.grails.commons.GrailsApplication
import net.greghaines.jesque.Job

import static net.greghaines.jesque.utils.ResqueConstants.WORKER;

import static net.greghaines.jesque.worker.WorkerEvent.JOB_EXECUTE;
import static net.greghaines.jesque.worker.WorkerEvent.JOB_FAILURE;
import static net.greghaines.jesque.worker.WorkerEvent.JOB_PROCESS;
import static net.greghaines.jesque.worker.WorkerEvent.JOB_SUCCESS;
import static net.greghaines.jesque.worker.WorkerEvent.WORKER_ERROR;
import static net.greghaines.jesque.worker.WorkerEvent.WORKER_POLL;
import static net.greghaines.jesque.worker.WorkerEvent.WORKER_START;
import static net.greghaines.jesque.worker.WorkerEvent.WORKER_STOP
import net.greghaines.jesque.worker.UnpermittedJobException
import net.greghaines.jesque.utils.ReflectionUtils
import java.util.concurrent.Callable;

class GrailsWorkerImpl extends WorkerImpl {

    BeanBuilder beanBuilder
    GrailsApplication grailsApplication

    public GrailsWorkerImpl(
            GrailsApplication grailsApplication,
            final Config config,
            final Collection<String> queues,
			final Collection<? extends Class<?>> jobTypes) {
        super(config, queues, jobTypes)

        this.grailsApplication = grailsApplication
        beanBuilder = new BeanBuilder()
    }

	protected void checkJobTypes(final Collection<? extends Class<?>> jobTypes) {
		if (jobTypes == null) {
			throw new IllegalArgumentException("jobTypes must not be null")
		}
		if( jobTypes.any{ it == null} ) {
            throw new IllegalArgumentException("jobType's members must not be null: " + jobTypes)
        }
	}

    public void addJobType(final Class<?> jobType) {
		if (jobType == null) {
			throw new IllegalArgumentException("jobType must not be null")
		}

		this.jobTypes.add(jobType)
	}

	protected void process(final Job job, final String curQueue) {
		this.listenerDelegate.fireEvent(JOB_PROCESS, this, curQueue, job, null, null, null)
		renameThread("Processing " + curQueue + " since " + System.currentTimeMillis())
		try {
			final String fullClassName = this.jobPackage ? this.jobPackage + "." + job.className : job.className
			final Class<?> clazz = ReflectionUtils.forName(fullClassName)
			if (!this.jobTypes.contains(clazz)) {
				throw new UnpermittedJobException(clazz)
			}
            def instance = createInstance(fullClassName)
			execute(job, curQueue, instance, job.args );
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
            result = instance.perform(*args)
			success(job, instance, result, curQueue);
		} finally {
			this.jedis.del(key(WORKER, this.name));
		}
	}
}
