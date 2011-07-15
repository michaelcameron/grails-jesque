package grails.plugin.jesque

class ExceptionJob {

    def perform() {
        throw new Exception("Oh no!")
    }
}
