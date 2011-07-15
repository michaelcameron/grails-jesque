Grails Jesque
=============

Jesque is an implementation of [Resque](https://github.com/defunkt/resque) in [Java](http://www.oracle.com/technetwork/java/index.html).
It is fully-interoperable with the [Ruby](http://www.ruby-lang.org/en/) and [Node.js](http://nodejs.org/) ([Coffee-Resque](https://github.com/technoweenie/coffee-resque)) implementations.

The grails jesque plugin uses [jesque](https://github/gresrun/jesque) and the grails redis plugin as a dependency.
While it uses jesque for the core functionality it makes it groovier to use in grails.

There is also a grails [jesque-web](https://github.com/michaelcameron/grails-jesque-web) plugin initially ported from the [jesque-web](https://github.com/gresrun/jesque-web) spring-mvc app, which itself was based on the Sinatra web app resque-web in [resque](https://github.com/defunkt/resque).
Either UI will allow you to see what's in your queues and view and re-process failed messages.

How do I use it?
----------------
Add the jesque plugin to grails, it will automatically pull in jesque with it's dependencies, and the grails redis plugin.

```bash
grails install-plugin jesque
```

You must also have [redis](http://redis.io) installed in your environment.


Example to enqueue

```groovy

class BackgroundJob {
    def someOtherService //auto-wiring supported

    def perform( arg1, arg2 ) {
        def domainObject = DomainClass.get(arg1) //GORM supported
        domainObject.message = arg2
        domainObject.save()
    }
}

class SomeOtherClass {
    def jesqueService

    def doWorkAsync() {
        jesqueService.enqueue( 'myQueueName', BackgroundJob, 1, 'hi there')
    }
}
```

Workers can be started manually by calling

```groovy
    jesqueService.startWorker( 'myQueueName', BackgroundJob )
```

or automatically upon start-up with the following config

```xml
grails {
    jesque {
        workers {
            someNameForYourWorkerPool {
                workers = 3 //defaults to 1
                queueNames = 'myQueueName' //or a list
                jobTypes = BackgroundJob //or a list
            }
        }
    }
}
```

The redis pool used is configured in the [redis](https://github.com/grails-plugins/grails-redis) plugin:

```xml
grails {
    redis {
        host = localhost //default
        prot = 6379 //default
    }
}
```

Jobs
----
Jobs should be placed in grails-app\jobs similar to the [Quartz](http://grails.org/Quartz+plugin) plugin.
However, to not clash with quartz, and to retain similarties with resque, the method to execute must be called perform.

Roadmap
----
* Ability to execute methods on services without creating a job object
* Wrap above ability automatically with annotation and dynamically creating a method with the same name + "Async" suffix

License
-------
Copyright 2011 Michael Cameron

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   <http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
