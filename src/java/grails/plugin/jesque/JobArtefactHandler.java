package grails.plugin.jesque;

import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;

import java.lang.reflect.Method;

public class JobArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "Job";
    public static final String PERFORM = "perform";

    public JobArtefactHandler() {
        super(TYPE, GrailsJobClass.class, DefaultGrailsJobClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        // class shouldn't be null and shoud ends with Job suffix
        if(clazz == null || !clazz.getName().endsWith(DefaultGrailsJobClass.JOB))
            return false;

        // and should have a perform() method with any signature
        //Method method = ReflectionUtils.findMethod(clazz, PERFORM, null);
        for( Method method : clazz.getDeclaredMethods() ) {
            if( method.getName().equals(PERFORM) )
                return true;
        }
        return false;
    }
}
