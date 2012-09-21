package grails.plugin.jesque

import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicReference
import org.codehaus.groovy.grails.lifecycle.ShutdownOperations

class JesqueSchedulerThreadService implements Runnable {

    static transactional = true
    static scope = 'singleton'

    protected static String hostName
    protected static final Integer IDLE_WAIT_TIME = 10 * 1000
    protected AtomicReference<JesqueScheduleThreadState> threadState = new AtomicReference(JesqueScheduleThreadState.New)
    protected Thread schedulerThread
    protected static Integer MAX_RETRY_ATTEMPTS = 120
    protected static Integer MAX_SLEEP_TIME_MS = 5 * 60 * 1000 //5 minutes

    protected static Random random = new Random()

    def jesqueSchedulerService

    JesqueSchedulerThreadService() {
        ShutdownOperations.addOperation({ this.stop() })
    }

    void startSchedulerThread() {
        schedulerThread = new Thread(this, "Jesque Scheduler Thread")
        schedulerThread.daemon = true
        schedulerThread.start()
    }

    public void run() {
        if( !threadState.compareAndSet(JesqueScheduleThreadState.New, JesqueScheduleThreadState.Running)) {
            throw new Exception("Cannot start scheduler thread, state was not the expected ${JesqueScheduleThreadState.New} state")
        }

        while( threadState.get() == JesqueScheduleThreadState.Running ) {
            withRetryUsingBackoff(MAX_RETRY_ATTEMPTS, MAX_SLEEP_TIME_MS) {
                mainThreadLoop()
            }
        }

        log.info "Stopping jesque scheduler thread because thread state changed to ${threadState.get()}"
    }


    public void mainThreadLoop() {
        //server checkin
        jesqueSchedulerService.serverCheckIn(getHostName(), new DateTime())
        jesqueSchedulerService.cleanUpStaleServers()

        DateTime findJobsUntil = new DateTime().plusMillis(IDLE_WAIT_TIME)
        Integer enqueueJobCount = jesqueSchedulerService.enqueueReadyJobs(findJobsUntil, getHostName())

        if( enqueueJobCount == 0 && threadState.get() == JesqueScheduleThreadState.Running )
            Thread.sleep(IDLE_WAIT_TIME)
    }

    public void stop(Integer waitMilliseconds = IDLE_WAIT_TIME + 2000, Boolean interrupt = false) {
        log.info "Stopping the jesque scheduler thread"
        threadState.set(JesqueScheduleThreadState.Stopped)
        try{
            schedulerThread.join(waitMilliseconds)
        } catch (InterruptedException ignore) {
            log.debug "Interrupted exception caught when trying to stop scheduler thread"
        }
        if( interrupt && schedulerThread.isAlive() )
            schedulerThread.interrupt()
    }

    public String getHostName() {
        if( !hostName ) {
            hostName = System.getProperty("jesque.hostname") ?: InetAddress.localHost.hostName
        }

        hostName
    }

    protected withRetryUsingBackoff(int attempts, long maxSleepTimeMs, Closure closure) {
        for(int attempt = 1; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if( attempt != 1 )
                    jesqueSchedulerService.cleanUpStaleServer(hostName)

                mainThreadLoop()

                break
            } catch(Exception exception) {
                log.error "Jesque scheduler exception, attempt $attempt of $MAX_RETRY_ATTEMPTS", exception
                if( attempt != MAX_RETRY_ATTEMPTS ) {
                    Double sleepTime = Math.min( MAX_SLEEP_TIME_MS, random.nextDouble() * Math.pow(2, attempt))
                    Thread.sleep(sleepTime.toLong())
                } else {
                    log.error "Could not run scheduler thread after $MAX_RETRY_ATTEMPTS attempts, stopping for good"
                    threadState.set(JesqueScheduleThreadState.Stopped)
                }
            }
        }
    }
}
