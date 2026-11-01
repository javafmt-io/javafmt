class Fixture {
    static final int SHIPPED = 1;

    static final int PAID = 2;

    static final int ARCHIVED = 4;

    static final int DEFAULTS = SHIPPED | PAID
    // | ARCHIVED
    ;

    static final int OPEN = (1 << SHIPPED) | (1 << PAID)
    // Not ok for archived orders
    ;

    static final int ALL = SHIPPED | PAID
    // Archived orders are left out on purpose:
    //| ARCHIVED);
    ;
}
