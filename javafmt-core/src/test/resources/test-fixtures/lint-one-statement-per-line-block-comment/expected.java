class Order {
    Object total;

    Order total(final Object value) {
        this.total = value; /* note; */
        return this;
    }
}
