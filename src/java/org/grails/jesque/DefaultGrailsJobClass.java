package org.grails.jesque;

import org.codehaus.groovy.grails.commons.AbstractInjectableGrailsClass;

public class DefaultGrailsJobClass extends AbstractInjectableGrailsClass implements GrailsJobClass {

    public static final String JOB = "Job";

    public DefaultGrailsJobClass(Class clazz) {
        super(clazz, JOB);
    }
}
