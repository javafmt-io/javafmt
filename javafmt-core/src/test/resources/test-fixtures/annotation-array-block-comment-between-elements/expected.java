class Ledger {

    @SuppressWarnings({
            "ShortCircuitBoolean",
            /*
             * Two threads are compared with ==, because a subclass of Thread
             * may override equals.
             */
            "ReferenceEquality",
    })
    boolean sameThread(final Thread first, final Thread second) {
        return first == second;
    }
}
