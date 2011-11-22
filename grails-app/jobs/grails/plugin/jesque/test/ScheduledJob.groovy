package grails.plugin.jesque.test

import org.joda.time.DateTime

class ScheduledJob {
    static triggers = {
        cron name:'ScheduledJob', jesqueJobName:ScheduledJob.simpleName, jesqueQueue:'queueName', cronExpression: "0/10 * * * * ?"
    }

    def perform() {
        println "You ran a simple job at ${new DateTime()}"
    }
}
