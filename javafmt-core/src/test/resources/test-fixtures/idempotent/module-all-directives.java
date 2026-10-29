module com.example.full {
    requires java.base;
    requires static java.compiler;
    requires transitive java.desktop;

    exports com.example.api;
    exports com.example.internal to com.example.consumer.one, com.example.consumer.two;

    opens com.example.reflect;
    opens com.example.config to com.example.consumer.one;

    uses com.example.api.Service;

    provides com.example.api.Service with com.example.impl.AlphaImpl, com.example.impl.BetaImpl;
}