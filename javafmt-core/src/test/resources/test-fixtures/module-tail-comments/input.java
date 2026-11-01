module com.example.orders {
    requires java.sql;

    exports com.example.orders;

    // No zero-argument constructor, so it cannot be a service yet
    //provides com.example.Service with
    //    com.example.orders.OrderService;
}
