package test

class ExceptionJob {

    def perform() {
        throw new Exception("Oh no!")
    }
}
