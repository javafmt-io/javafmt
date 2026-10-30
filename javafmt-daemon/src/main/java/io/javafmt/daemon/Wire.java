package io.javafmt.daemon;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum Wire {

    BINARY,
    JSON;

    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();

    private static final JsonFormat.Parser PARSER = JsonFormat.parser();

    Stream<byte[]> frames(final InputStream in) {
        return switch (this) {
            case BINARY -> Stream.generate(() -> frame(in)).takeWhile(Optional::isPresent).map(Optional::orElseThrow);
            case JSON ->
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().map(line -> line.getBytes(StandardCharsets.UTF_8));
        };
    }

    <B extends Message.Builder> B decode(final byte[] frame, final B builder) throws InvalidProtocolBufferException {
        switch (this) {
            case BINARY -> builder.mergeFrom(frame);
            case JSON -> PARSER.merge(new String(frame, StandardCharsets.UTF_8), builder);
        }
        return builder;
    }

    byte[] encode(final Message message) {
        try {
            return switch (this) {
                case BINARY -> delimited(message);
                case JSON -> (PRINTER.print(message) + "\n").getBytes(StandardCharsets.UTF_8);
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] delimited(final Message message) throws IOException {
        final var out = new ByteArrayOutputStream(message.getSerializedSize() + 5);
        message.writeDelimitedTo(out);
        return out.toByteArray();
    }

    private static Optional<byte[]> frame(final InputStream in) {
        try {
            final var first = in.read();
            if (first < 0) {
                return Optional.empty();
            }
            final var size = CodedInputStream.readRawVarint32(first, in);
            final var frame = in.readNBytes(size);
            if (frame.length < size) {
                log.warn("dropped a request cut short by the end of input");
                return Optional.empty();
            }
            return Optional.of(frame);
        } catch (final IOException | IllegalArgumentException e) {
            log.warn("stopped reading requests: the input is not length-delimited protobuf", e);
            return Optional.empty();
        }
    }
}
