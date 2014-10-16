package grails.plugin.jesque

import net.greghaines.jesque.Job

/**
 * Exception handler that handles exceptions thrown by a jobs perform() method.
 */
interface JobExceptionHandler {

    def onException(Exception exception, Job job, String curQueue)

}
