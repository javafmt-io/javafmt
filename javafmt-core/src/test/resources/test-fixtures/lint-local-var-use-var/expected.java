import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Fixture {

    void build() {
        final var _ = new Object();
    }

    void invoice(final Object amount, final List<String> others) {
        final var lines = new ArrayList<String>();
        final var totals = new HashMap<String, Integer>();
        final var total = (Object) amount;
        final var currency = "EUR";
        final var count = 1;
        final long cents = 1;
        final Runnable task = () -> {};
        List<String> notes = new ArrayList<>();
        notes = others;
        System.out.println(lines + " " + totals + total + currency + count + cents + task + notes);
    }
}
