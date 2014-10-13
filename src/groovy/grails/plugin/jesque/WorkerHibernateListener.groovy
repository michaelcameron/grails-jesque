package grails.plugin.jesque

import net.greghaines.jesque.worker.WorkerListener
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.Job
import org.springframework.orm.hibernate4.SessionFactoryUtils
import org.springframework.orm.hibernate4.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.hibernate.Session
import org.hibernate.FlushMode
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory

class WorkerHibernateListener implements WorkerListener {

    def sessionFactory
    def boundByMe = false
    static log = LogFactory.getLog(WorkerHibernateListener)

    WorkerHibernateListener(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory
    }

    void onEvent(WorkerEvent workerEvent, Worker worker, String s, Job job, Object o, Object o1, Exception e) {
        log.debug("Processing worker event ${workerEvent.name()}")
        if( workerEvent == WorkerEvent.JOB_EXECUTE ) {
            boundByMe = bindSession()
        } else if( workerEvent in [WorkerEvent.JOB_SUCCESS, WorkerEvent.JOB_FAILURE]) {
            unbindSession()
        }
    }

    private boolean bindSession() {
        if(sessionFactory == null)
            throw new IllegalStateException("No sessionFactory property provided");

        log.debug("Binding session")

        final Object inStorage = TransactionSynchronizationManager.getResource(sessionFactory);
        if(inStorage != null) {
            ((SessionHolder)inStorage).getSession().flush();
            return false;
        } else {
            Session session = sessionFactory.currentSession
            session.setFlushMode(FlushMode.AUTO);
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session));
            return true;
        }
    }

    private void unbindSession() {
        if(sessionFactory == null)
            throw new IllegalStateException("No sessionFactory property provided");

        log.debug("Unbinding session")

        if( boundByMe ) {
            SessionHolder sessionHolder = (SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory);
            try {
                if(!FlushMode.MANUAL.equals(sessionHolder.getSession().getFlushMode())) {
                    sessionHolder.getSession().flush();
                }
            } catch(Exception e) {
                fireThreadException(e);
            } finally {
                TransactionSynchronizationManager.unbindResource(sessionFactory);
                SessionFactoryUtils.closeSession(sessionHolder.getSession());
            }
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
