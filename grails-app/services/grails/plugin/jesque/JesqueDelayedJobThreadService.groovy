package grails.plugin.jesque

import org.springframework.beans.factory.DisposableBean

import java.util.concurrent.atomic.AtomicReference
import org.joda.time.DateTime

class JesqueDelayedJobThreadService implements Runnable, DisposableBean {
    static transactional = true
    static scope = 'singleton'

    protected static final Integer IDLE_WAIT_TIME = 10 * 1000
    protected AtomicReference<JesqueDelayedJobThreadState> threadState = new AtomicReference(JesqueDelayedJobThreadState.New)
    protected Thread delayedJobThread
    protected static Integer MAX_RETRY_ATTEMPTS = 120
    protected static Integer MAX_SLEEP_TIME_MS = 5 * 60 * 1000 //5 minutes

    protected static Random random = new Random()

    protected long maxSleepTimeMs

    def jesqueDelayedJobService
    def grailsApplication

    void startThread() {
        delayedJobThread = new Thread(this, "Jesque Delayed Job Thread")
        delayedJobThread.daemon = true
        delayedJobThread.start()

        maxSleepTimeMs = grailsApplication.config.grails.jesque.delayedJobMaxSleepTimeMs ?: 500
    }

    public void run() {
        if( !threadState.compareAndSet(JesqueDelayedJobThreadState.New, JesqueDelayedJobThreadState.Running)) {
            throw new Exception("Cannot start delayed job thread, state was not the expected ${JesqueDelayedJobThreadState.New} state")
        }

        while( threadState.get() == JesqueDelayedJobThreadState.Running ) {
            withRetryUsingBackoff(MAX_RETRY_ATTEMPTS, MAX_SLEEP_TIME_MS) {
                mainThreadLoop()
            }
        }

        log.info "Stopping jesque delayed job thread because thread state changed to ${threadState.get()}"
    }

    public void mainThreadLoop() {
        jesqueDelayedJobService.enqueueReadyJobs()

        def nextFireTime = jesqueDelayedJobService.nextFireTime()
        def timeToNextJobMs = nextFireTime.millis - new DateTime().millis
        if( timeToNextJobMs < 0 )
            return
        else if (timeToNextJobMs < maxSleepTimeMs )
            Thread.sleep(timeToNextJobMs)
        else
            Thread.sleep(maxSleepTimeMs)
    }

    public void stop(Integer waitMilliseconds = IDLE_WAIT_TIME + 2000, Boolean interrupt = false) {
        log.info "Stopping the jesque delayed job thread"
        threadState.set(JesqueDelayedJobThreadState.Stopped)
        try{
            delayedJobThread.join(waitMilliseconds)
        } catch (InterruptedException ignore) {
            log.debug "Interrupted exception caught when trying to stop delayed job thread"
        }
        if( interrupt && delayedJobThread.isAlive() )
            delayedJobThread.interrupt()
    }

    protected withRetryUsingBackoff(int attempts, long maxSleepTimeMs, Closure closure) {
        for(int attempt = 1; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return closure()
            } catch(Exception exception) {
                log.error "Jesque delayed job exception, attempt $attempt of $MAX_RETRY_ATTEMPTS", exception

                if( threadState.get() != JesqueDelayedJobThreadState.Running ) {
                    log.info "Aborting retries because thread state is ${threadState.get()}"
                } else if( attempt != MAX_RETRY_ATTEMPTS ) {
                    Double sleepTime = Math.min( MAX_SLEEP_TIME_MS, 500 + random.nextDouble() * Math.pow(2, attempt))
                    Thread.sleep(sleepTime.toLong())
                } else {
                    log.error "Could not run delayed job thread after $MAX_RETRY_ATTEMPTS attempts, stopping for good"
                    threadState.set(JesqueDelayedJobThreadState.Stopped)
                }
            }
        }
    }

    void destroy()  {
        this.stop()
    }
}
