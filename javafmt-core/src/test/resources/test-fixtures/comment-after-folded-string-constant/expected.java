import java.nio.charset.StandardCharsets;

final class Codes {

    private static final byte[] DIGITS = ("0123456789" // the digits
            + "abcdef") // then the letters
                    .getBytes(StandardCharsets.US_ASCII);

    private static final int SIZE = DIGITS.length;

    private Codes() {}
}
