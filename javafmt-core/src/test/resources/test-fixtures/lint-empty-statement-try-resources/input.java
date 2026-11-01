import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

class Fixture {
    int read(final InputStream source) throws IOException {
        try (DataInputStream input = new DataInputStream(source); ) {
            return input.readInt();
        }
    }
}
