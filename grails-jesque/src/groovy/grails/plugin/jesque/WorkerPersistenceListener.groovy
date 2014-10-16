package grails.plugin.jesque

import net.greghaines.jesque.worker.WorkerListener
import net.greghaines.jesque.worker.WorkerEvent
import net.greghaines.jesque.worker.Worker
import net.greghaines.jesque.Job
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor
import org.apache.commons.logging.LogFactory

class WorkerPersistenceListener implements WorkerListener {

    PersistenceContextInterceptor persistenceInterceptor
    Boolean boundByMe = false
    static log = LogFactory.getLog(WorkerPersistenceListener)

    WorkerPersistenceListener(PersistenceContextInterceptor persistenceInterceptor) {
        this.persistenceInterceptor = persistenceInterceptor
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
        if(persistenceInterceptor == null)
            throw new IllegalStateException("No persistenceInterceptor property provided")

        log.debug("Binding session")

        if( persistenceInterceptor.isOpen() ) {
            boundByMe = true
            persistenceInterceptor.init()
        }
    }

    private void unbindSession() {
        if(persistenceInterceptor == null)
            throw new IllegalStateException("No persistenceInterceptor property provided")

        log.debug("Unbinding session")

        if( boundByMe ) {
            try {
                persistenceInterceptor.flush()
            } catch(Exception exception) {
                fireThreadException(exception)
            } finally {
                persistenceInterceptor.destroy()
            }
        }
    }

    private static void fireThreadException(final Exception exception) {
        final Thread thread = Thread.currentThread()
        if (thread.uncaughtExceptionHandler == null) {
            //Logging the problem that the current thread doesn't have an uncaught exception handler set.
            //Bare throwing an exception might not have any effect in such a case.
            final String message = "No handler property provided for the current background worker thread ${thread.name} when trying to handle an exception."
            log.error(message, exception)
        } else {
            thread.uncaughtExceptionHandler.uncaughtException(thread, exception)
        }
    }
}
