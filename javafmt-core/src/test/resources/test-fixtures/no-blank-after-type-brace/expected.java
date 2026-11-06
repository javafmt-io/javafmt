package shipping;

public class OrderService {

    private final int limit = 5;

    /** Counts the open orders. */
    int openOrders() {
        return limit;
    }

    Runnable audit() {
        return new Runnable() {
            private int runs;

            @Override
            public void run() {
                runs++;
            }

            int total() {
                return runs;
            }
        };
    }

    Runnable commented() {
        return new Runnable() {
            // only member
            @Override
            public void run() {}
        };
    }

    static class Invoice {

        // the invoice number
        String number;

        void pay() {}
    }

    interface Shipment {

        /** Weight in grams. */
        int weight();
    }

    enum Status {

        OPEN,
        CLOSED;

        // readable label
        String label() {
            return name();
        }
    }

    record Receipt(String id) {

        /** Formatted id. */
        String formatted() {
            return id;
        }
    }
}
