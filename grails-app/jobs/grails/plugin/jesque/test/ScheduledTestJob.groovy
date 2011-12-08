package grails.plugin.jesque.test

import org.joda.time.DateTime

class ScheduledTestJob {

    static triggers = {
        cron name:'ScheduledJob', jesqueJobName:ScheduledTestJob.simpleName, jesqueQueue:'queueName', cronExpression: "0/10 * * * * ?", timeZone: 'Pacific/Honolulu', args:['MyArgument']
    }

    def perform(arg) {
        println "You ran a simple job at ${new DateTime()} with argument ${arg}"
    }
}
