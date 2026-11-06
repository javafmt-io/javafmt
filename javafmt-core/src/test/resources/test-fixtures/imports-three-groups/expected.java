import jakarta.annotation.Nullable;
import lombok.Builder;
import org.example.A; // why A

import java.time.Duration;
import javax.inject.Inject;

import static xyz.U.u;

class Invoice {

    @Nullable
    @Builder
    Duration due;

    @Inject
    A account;

    int total() {
        return u();
    }
}
