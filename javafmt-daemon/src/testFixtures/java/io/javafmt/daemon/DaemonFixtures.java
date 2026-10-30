package io.javafmt.daemon;

import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Message;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class DaemonFixtures {

    public static final List<String> REQUESTS_HEX = lines("requests.hex");

    public static final List<String> REQUESTS_JSON = lines("requests.jsonl");

    public static final List<String> REPLIES_HEX = lines("replies.hex");

    public static final List<String> REPLIES_JSON = lines("replies.jsonl");

    public static final String LARGE_REPLY_HEX = lines("large-reply.hex").getFirst();

    public static final String LARGE_REPLY_JSON = lines("large-reply.jsonl").getFirst();

    private static final Validator VALIDATOR = ValidatorFactory.newBuilder().build();

    public static byte[] bytes(final List<String> hexFrames) {
        return HexFormat.of().parseHex(String.join("", hexFrames));
    }

    public static String text(final List<String> jsonLines) {
        return jsonLines.stream().map(line -> line + "\n").reduce("", String::concat);
    }

    public static List<String> violations(final Message message) {
        try {
            return VALIDATOR.validate(message).getViolations().stream().map(violation -> violation.toProto().getMessage()).toList();
        } catch (final ValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<String> hexFrames(final byte[] stream) {
        final var in = CodedInputStream.newInstance(stream);
        return Stream.generate(() -> frame(in, stream))
                .takeWhile(Optional::isPresent)
                .map(Optional::orElseThrow)
                .toList();
    }

    private static Optional<String> frame(final CodedInputStream in, final byte[] stream) {
        try {
            if (in.isAtEnd()) {
                return Optional.empty();
            }
            final var start = in.getTotalBytesRead();
            in.skipRawBytes(in.readRawVarint32());
            return Optional.of(HexFormat.of().formatHex(stream, start, in.getTotalBytesRead()));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(final String name) {
        try (final var in = DaemonFixtures.class.getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DaemonFixtures() {}
}
