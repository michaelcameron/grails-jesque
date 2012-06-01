package grails.plugin.jesque;

import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;

import java.lang.reflect.Method;

public class JesqueJobArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "JesqueJob";
    public static final String PERFORM = "perform";

    public JesqueJobArtefactHandler() {
        super(TYPE, GrailsJesqueJobClass.class, DefaultGrailsJesqueJobClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        // class shouldn't be null and should end with Job suffix
        if(clazz == null || !clazz.getName().endsWith(DefaultGrailsJesqueJobClass.JOB))
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
