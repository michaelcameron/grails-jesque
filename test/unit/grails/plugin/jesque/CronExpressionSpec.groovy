package grails.plugin.jesque

import org.joda.time.DateTime
import spock.lang.Specification

import java.text.ParseException

class CronExpressionSpec extends Specification {
    void "test isSatisfiedBy"() {
        given:
        def cronExpression = new CronExpression("0 15 10 * * ? 2005")

        expect:
        cronExpression.isSatisfiedBy(new DateTime(2005, 6, 1, 10, 15, 0))
        cronExpression.isSatisfiedBy(new DateTime(2005, 6, 2, 10, 15, 0))
        cronExpression.isSatisfiedBy(new DateTime(2005, 7, 2, 10, 15, 0))
        !cronExpression.isSatisfiedBy(new DateTime(2005, 7, 2, 10, 16, 0))
        !cronExpression.isSatisfiedBy(new DateTime(2005, 7, 2, 11, 15, 0))
        !cronExpression.isSatisfiedBy(new DateTime(2006, 6, 1, 10, 15, 0))
    }

    void "test last day offset"() {
        when:
        def cronExpression = new CronExpression("0 15 10 L-2 * ? 2010")

        then:
        cronExpression.isSatisfiedBy(new DateTime(2010, 10, 29, 10, 15, 0))
        !cronExpression.isSatisfiedBy(new DateTime(2010, 10, 28, 10, 15, 0))
        cronExpression.isSatisfiedBy(new DateTime(2010, 10, 29, 10, 15, 0))

        when:
        cronExpression = new CronExpression("0 15 10 L-5W * ? 2010")

        then:
        cronExpression.isSatisfiedBy(new DateTime(2010, 10, 26, 10, 15, 0))

        when:
        cronExpression = new CronExpression("0 15 10 L-1 * ? 2010")

        then:
        cronExpression.isSatisfiedBy(new DateTime(2010, 10, 30, 10, 15, 0))

        when:
        cronExpression = new CronExpression("0 15 10 L-1W * ? 2010")

        then:
        // nearest weekday to last day - 1 (29th is a friday in 2010)
        cronExpression.isSatisfiedBy(new DateTime(2010, 10, 29, 10, 15, 0))
    }

    void "test serialization and deserialization"() {
        when:
        CronExpression cronExpression = new CronExpression("19 15 10 4 Apr ? ");

        def baos = new ByteArrayOutputStream()
        new ObjectOutputStream(baos).writeObject(cronExpression)

        def ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())){
            protected Class resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
                return Class.forName(objectStreamClass.getName(), true, CronExpression.class.classLoader)
            }
        }
        CronExpression newExpression = (CronExpression)ois.readObject()

        then:
        noExceptionThrown()
        newExpression.cronExpression == cronExpression.cronExpression
        newExpression.getNextValidTimeAfter(new Date()) != null
    }

    void "test invalid months"() {
        when:
        new CronExpression("* * * * Foo ? ")

        then:
        def exception = thrown(ParseException)
        exception.message.startsWith("Invalid Month value:")

        when:
        new CronExpression("* * * * Jan-Foo ? ")

        then:
        exception = thrown(ParseException)
        exception.message.startsWith("Invalid Month value:")
    }

    public void "test invalid day-of-week and day-of-month together"() {
        when:
        new CronExpression("0 0 * * * *")

        then:
        def exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.")

        when:
        new CronExpression("0 0 * 4 * *")

        then:
        exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.")

        when:
        new CronExpression("0 0 * * * 4")

        then:
        exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.")
    }


    public void "test invalid and valid L expressions"() throws ParseException {
        when:
        new CronExpression("0 43 9 1,5,29,L * ?")

        then:
        def exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying 'L' and 'LW' with other days of the month is not implemented")

        when:
        new CronExpression("0 43 9 ? * SAT,SUN,L")

        then:
        exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying 'L' with other days of the week is not implemented")


        when:
        new CronExpression("0 43 9 ? * 6,7,L")

        then:
        exception = thrown(ParseException)
        exception.message.startsWith("Support for specifying 'L' with other days of the week is not implemented")

        when:
        new CronExpression("0 43 9 ? * 5L")

        then:
        noExceptionThrown()
    }

    public void "test invalid W values"() throws ParseException {
        when:
        new CronExpression("0/5 * * 32W 1 ?")

        then:
        def exception = thrown(ParseException)
        exception.message.startsWith("The 'W' option does not make sense with values larger than")
    }
}
