package org.grails.jesque.test

class SimpleAction implements Runnable {

    void run() {
        println "you ran a simple action"
    }
}
