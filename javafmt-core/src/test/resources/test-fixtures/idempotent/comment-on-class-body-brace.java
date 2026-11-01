class Order { // the root of every invoice
    final Runnable task = new Runnable() { // runs once
        @Override
        public void run() {}
    };

    enum Status {
        OPEN { // the first state
            @Override
            boolean done() {
                return false;
            }
        },
        CLOSED { // the last state
            @Override
            boolean done() {
                return true;
            }
        };

        abstract boolean done();
    }
}
