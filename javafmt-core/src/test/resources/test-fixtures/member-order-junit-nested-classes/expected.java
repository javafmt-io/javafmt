import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class InvoiceTest {
    private InvoiceTest() {}

    @Test
    void totalsEmptyInvoice() {
        check(0);
    }

    @Nested
    class WhenPaid {
        @Test
        void marksPaid() {
            check(1);
        }

        record Receipt(int amount) {}
    }

    @Nested
    @DisplayName("when overdue")
    class WhenOverdue {
        @Test
        void addsFee() {
            check(2);
        }
    }

    @org.junit.jupiter.api.Nested
    class WhenRefunded {
        @Test
        void reversesTotal() {
            check(3);
        }
    }

    @Test
    void totalsOneLine() {
        check(4);
    }

    record Line(String item, int quantity) {}

    interface Fixture {}
}
