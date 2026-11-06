import lombok.Builder;
import org.example.Order;

import static xyz.U.u;

class Shipment {

    @Builder
    Order order;

    int count() {
        return u();
    }
}
