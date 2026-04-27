import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class Fixture {
    String readBytes(Path p) {
        try {
            byte[] data = Files.readAllBytes(p);
            String result = new String(data);
            return result;
        } catch (IOException e) {
            String message = e.getMessage();
            return message;
        }
    }

    long count(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            long n = in.transferTo(java.io.OutputStream.nullOutputStream());
            return n;
        }
    }
}
