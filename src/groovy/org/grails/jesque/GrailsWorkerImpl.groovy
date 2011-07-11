package org.grails.jesque

import net.greghaines.jesque.worker.WorkerImpl
import net.greghaines.jesque.Config
import net.greghaines.jesque.Job

import static net.greghaines.jesque.utils.ResqueConstants.WORKER
import static net.greghaines.jesque.worker.WorkerEvent.JOB_EXECUTE
import java.util.concurrent.Callable
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.orm.hibernate3.SessionHolder
import org.hibernate.Session
import org.springframework.orm.hibernate3.SessionFactoryUtils
import org.hibernate.FlushMode
import org.hibernate.SessionFactory


class GrailsWorkerImpl extends WorkerImpl {

    SessionFactory sessionFactory

    public GrailsWorkerImpl(
            SessionFactory sessionFactory,
            final Config config,
            final Collection<String> queues,
			final Collection<? extends Class<?>> jobTypes) {
        super(config, queues, jobTypes)

        this.sessionFactory = sessionFactory
    }

    private void execute(final Job job, final String currentQueue, final Object instance) {
		this.jedis.set(key(WORKER, this.name), statusMsg(currentQueue, job))
		try {
			final Object result
			this.listenerDelegate.fireEvent(JOB_EXECUTE, this, currentQueue, job, instance, null, null)
            boolean boundByMe = false
            try{
                boundByMe = bindSession()
                if (instance instanceof Callable) {
                    result = ((Callable<?>) instance).call() // The job is executing!
                } else if (instance instanceof Runnable) {
                    ((Runnable) instance).run() // The job is executing!
                    result = null
                } else {
                    // Should never happen since we're testing the class earlier
                    throw new ClassCastException("instance must be a Runnable or a Callable: ${instance.class.name} - ${instance}")
                }
            } catch(Exception e) {
                fireThreadException(e)
            } finally {
                if( boundByMe ) unbindSession()
            }
			success(job, instance, result, currentQueue)
		} finally {
			this.jedis.del(key(WORKER, this.name))
		}
    }

    private boolean bindSession() {
        if(sessionFactory == null)
            throw new IllegalStateException("No sessionFactory property provided");

        final Object inStorage = TransactionSynchronizationManager.getResource(sessionFactory);
        if(inStorage != null) {
            ((SessionHolder)inStorage).getSession().flush();
            return false;
        } else {
            Session session = SessionFactoryUtils.getSession(sessionFactory, true);
            session.setFlushMode(FlushMode.AUTO);
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session));
            return true;
        }
    }

    private void unbindSession() {
        if(sessionFactory == null)
            throw new IllegalStateException("No sessionFactory property provided");

        SessionHolder sessionHolder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory);
        try {
            if(!FlushMode.MANUAL.equals(sessionHolder.getSession().getFlushMode())) {
                log.info("FLUSHING SESSION IN BACKGROUND");
                sessionHolder.getSession().flush();
            }
        } catch(Exception e) {
            fireThreadException(e);
        } finally {
            TransactionSynchronizationManager.unbindResource(sessionFactory);
            SessionFactoryUtils.closeSession(sessionHolder.getSession());
        }
    }

    private static void fireThreadException(final Exception e) {
        final Thread thread = Thread.currentThread();
        if (thread.getUncaughtExceptionHandler() == null) {
            //Logging the problem that the current thread doesn't have an uncaught exception handler set.
            //Bare throwing an exception might not have any effect in such a case.
            final String message = "No handler property provided for the current background worker thread " + thread.getName()
                    + " when trying to handle an exception. ";
            log.error(message, e);
        } else {
            thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
        }
    }
}
