import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Fixture {
    void build() {
        Object value = new Object();
    }

    void invoice(Object amount, List<String> others) {
        List<String> lines = new ArrayList<String>();
        Map<String, Integer> totals = new HashMap<>();
        Object total = (Object) amount;
        String currency = "EUR";
        int count = 1;
        long cents = 1;
        Runnable task = () -> {};
        List<String> notes = new ArrayList<String>();
        notes = others;
        System.out.println(lines + " " + totals + total + currency + count + cents + task + notes);
    }
}
