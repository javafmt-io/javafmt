module com.example.sorting {
    exports com.example.api to zeta.consumer, alpha.consumer, mu.consumer;
    opens com.example.reflect to zeta.consumer, alpha.consumer;
}
