class Statements {
    int run(final int[] values, final java.io.Reader in) throws java.io.IOException {
        var total = 0;
        outer:
        for (var i = 0; i < values.length; i++) {
            if (values[i] < 0) {
                continue outer;
            } else if (values[i] == 0) {
                break;
            } else {
                total += values[i];
            }
        }
        for (final var value : values) {
            total -= value;
        }
        while (total > 100) {
            total /= 2;
        }
        do {
            total++;
        } while (total < 0);
        synchronized (this) {
            total *= 2;
        }
        try (var reader = new java.io.BufferedReader(in)) {
            total += reader.read();
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        } finally {
            total--;
        }
        assert total >= 0 : "negative";
        return total;
    }
}
