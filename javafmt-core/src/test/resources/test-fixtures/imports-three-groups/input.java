import static xyz.U.u;
import java.time.Duration;
import jakarta.annotation.Nullable;
import javax.inject.Inject;
import org.example.A; // why A
import lombok.Builder;

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
