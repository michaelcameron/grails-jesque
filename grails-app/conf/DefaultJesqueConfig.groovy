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
                schedulerThreadActive = false
            }
        }
    }
}