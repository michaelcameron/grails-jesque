package grails.plugin.jesque

import org.joda.time.DateTime

class JesqueSchedulerThreadService implements Runnable {

    static transactional = true

    protected static String hostName

    def jesqueSchedulerService

    protected static Integer IDLE_WAIT_TIME = 10 * 1000

    void startSchedulerThread() {
        def schedulerThread = new Thread(this, "Jesque Scheduler Thread")
        schedulerThread.daemon = true
        schedulerThread.start()
    }

    public void run() {
        while( true ) {
            //server checkin
            jesqueSchedulerService.serverCheckIn(getHostName(), new DateTime())
            jesqueSchedulerService.cleanUpStaleServers()

            DateTime findJobsUntil = new DateTime().plusMillis(IDLE_WAIT_TIME)
            Integer enqueueJobCount = jesqueSchedulerService.enqueueReadyJobs(findJobsUntil, getHostName())

            if( enqueueJobCount == 0)
                Thread.sleep(IDLE_WAIT_TIME)
        }
    }

    public String getHostName() {
        if( !hostName ) {
            hostName = System.getProperty("jesque.hostname") ?: InetAddress.localHost.hostName
        }

        hostName
    }
}
