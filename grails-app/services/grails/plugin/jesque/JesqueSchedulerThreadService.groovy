package grails.plugin.jesque

import org.joda.time.DateTime

class JesqueSchedulerThreadService implements Runnable {

    static transactional = true

    def jesqueSchedulerService

    protected static Integer IDLE_WAIT_TIME = 30 * 1000

    void startSchedulerThread() {
        def schedulerThread = new Thread(this, "Jesque Scheduler Thread")
        schedulerThread.daemon = true
        schedulerThread.start()
    }

    public void run() {
        while( true ) {
            DateTime findJobsUntil = new DateTime().plusMillis(IDLE_WAIT_TIME)
            Integer enqueueJobCount = jesqueSchedulerService.enqueueReadyJobs(findJobsUntil)

            if( enqueueJobCount == 0)
                Thread.sleep(IDLE_WAIT_TIME)
        }
    }

}
