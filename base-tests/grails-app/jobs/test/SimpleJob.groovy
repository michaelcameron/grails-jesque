package test

import org.joda.time.DateTime

class SimpleJob {

    void perform() {
        println "you ran a simple action at ${new DateTime()}"
    }
}
