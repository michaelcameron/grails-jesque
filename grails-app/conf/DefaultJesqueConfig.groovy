grails{
    jesque {
        pruneWorkersOnStartup = true
        createWorkersOnStartup = true
        schedulerThreadActive = true
        delayedJobThreadActive = true
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