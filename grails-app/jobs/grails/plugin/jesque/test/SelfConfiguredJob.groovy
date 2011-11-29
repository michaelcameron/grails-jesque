package grails.plugin.jesque.test

class SelfConfiguredJob {

    static queue = "MyQueue"
    static workerPool = "MyWorkerPool"
    static jobNames = ['MyJobName']

    def perform() {
    }
}
