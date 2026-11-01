import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntegrationTest(classes = {OrderService.class, OrderConfiguration.class}, imports = {SecurityConfiguration.class, PersistenceConfiguration.class},
        properties = {"orders.enabled=true", "orders.endpoint=http://localhost:8080/orders"})
class OrderIntegrationTest {
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation", "removal", "serial", "static-access", "unused", "cast", "fallthrough", "finally", "this-escape"})
    void settle() {}

    @IntegrationTest(
        classes = {OrderService.class},
        imports = {},
        properties = {}
    )
    void ship() {}

    @IntegrationTest(classes = {OrderService.class, OrderConfiguration.class, SecurityConfiguration.class, PersistenceConfiguration.class}, imports = {}, properties = {"orders.enabled=true", "orders.endpoint=http://localhost:8080/orders", "orders.retries=3", "orders.timeout=30s", "orders.currency=EUR"})
    void invoice() {}
}

@Retention(RetentionPolicy.RUNTIME)
@interface IntegrationTest {
    Class<?>[] classes();

    Class<?>[] imports();

    String[] properties();
}

class OrderService {}

class OrderConfiguration {}

class SecurityConfiguration {}

class PersistenceConfiguration {}
