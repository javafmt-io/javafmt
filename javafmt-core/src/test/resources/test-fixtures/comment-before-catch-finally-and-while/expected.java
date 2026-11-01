import java.util.concurrent.Callable;

class Fixture {
    Object read(final Callable<Object> task) {
        try {
            return task.call();
        }
        // These can happen when callers hand over a task
        // that does not keep the contract.
        catch (final IllegalStateException
                | IllegalArgumentException
                | NullPointerException
                | ClassCastException
                | UnsupportedOperationException
                | IndexOutOfBoundsException _) {
            return null;
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
        // Always runs.
        finally {
            task.hashCode();
        }
    }

    void spin(final int[] counter) {
        do {
            counter[0]++;
        }
        // Until the counter wraps.
        while (counter[0] > 0);
    }
}
