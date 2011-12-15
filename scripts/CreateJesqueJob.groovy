import org.apache.tools.ant.taskdefs.Ant

/*
* Copyright 2006-2008 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
 * Gant script that creates a Grails Jesque job
 *
 * @author Christian Oestreich
 */
includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsCreateArtifacts")

target('default': "Creates a new Jesque job") {
    depends(checkVersion, parseArguments)

    def name = argsMap["params"][0]
    def suffix = name.endsWith('Job') ? '' : "Job"
    def type = "JesqueJob"
    promptForName(type: type)
    createArtifact(name: name, suffix: suffix, type: type, path: "grails-app/jobs")
    createUnitTest(name: name, suffix: suffix)
    if(hasSpockInstalled()) {
        createArtifact(name: name, suffix: "${suffix}Spec", type: "${type}Spec", path: "test/integration")
    } else {
        createArtifact(name: name, suffix: "${suffix}IntegrationTests", type: "${type}IntegrationTests", path: "test/integration")
    }
}

private boolean hasSpockInstalled() {
    for(file in grailsSettings.testDependencies) {
        if(file?.name?.contains('spock-core')) {
            return true
        }
    }
    return false
}

