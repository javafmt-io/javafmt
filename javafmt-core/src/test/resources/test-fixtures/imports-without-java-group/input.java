import static xyz.U.u;
import org.example.Order;
import lombok.Builder;

class Shipment {

    @Builder
    Order order;

    int count() {
        return u();
    }
}
