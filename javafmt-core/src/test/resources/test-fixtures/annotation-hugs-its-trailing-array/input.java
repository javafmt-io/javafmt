import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SpringSimpleTest(classes = Fixture.class, properties = {"radar.event.action-notification.enabled=true", "radar.event.action-notification.target.associate=" + Fixture.TARGET_ASSOCIATE, "radar.event.action-notification.topic=" + Fixture.TOPIC})
class Fixture {
    static final String TARGET_ASSOCIATE = "associate";
    static final String TOPIC = "topic";

    @SuppressWarnings({"radar.event.action-notification.enabled.first.long.value.to.force.a.break", "radar.event.action-notification.enabled.second", "third"})
    void suppressed() {}

    @SpringSimpleTest(classes = Fixture.class, properties = {"short", "list"})
    void fits() {}
}

@Retention(RetentionPolicy.RUNTIME)
@interface SpringSimpleTest {
    Class<?>[] classes();

    String[] properties();
}
