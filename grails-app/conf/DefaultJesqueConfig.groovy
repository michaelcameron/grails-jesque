grails{
    jesque {
        pruneWorkersOnStartup = true
        schedulerThreadActive = true
    }
}

environments {
    test {
        grails{
            jesque {
                pruneWorkersOnStartup = false
                schedulerThreadActive = false
            }
        }
    }
}