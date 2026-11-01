import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class InvoiceTest {
    private InvoiceTest() {}

    record Line(String item, int quantity) {}

    @Test
    void totalsEmptyInvoice() {
        check(0);
    }

    @Nested
    class WhenPaid {
        record Receipt(int amount) {}

        @Test
        void marksPaid() {
            check(1);
        }
    }
    @Nested
    @DisplayName("when overdue")
    class WhenOverdue {
        @Test
        void addsFee() {
            check(2);
        }
    }

    interface Fixture {}
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
}
