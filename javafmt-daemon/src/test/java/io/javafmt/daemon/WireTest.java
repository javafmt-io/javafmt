package io.javafmt.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WireTest {

    static Stream<Integer> requests() {
        return IntStream.range(0, DaemonFixtures.REQUESTS_HEX.size()).boxed();
    }

    static Stream<Integer> replies() {
        return IntStream.range(0, DaemonFixtures.REPLIES_HEX.size()).boxed();
    }

    @ParameterizedTest
    @MethodSource("requests")
    void eachRequestFixtureIsTheSameRequestOnBothWires(final int index) {
        final var request = decode(Wire.BINARY, DaemonFixtures.REQUESTS_HEX, index, FormatRequest.newBuilder());
        assertThat(decode(Wire.JSON, DaemonFixtures.REQUESTS_JSON, index, FormatRequest.newBuilder())).isEqualTo(request);
        assertEncodes(request, DaemonFixtures.REQUESTS_HEX.get(index), DaemonFixtures.REQUESTS_JSON.get(index));
    }

    @ParameterizedTest
    @MethodSource("replies")
    void eachReplyFixtureIsTheSameReplyOnBothWires(final int index) {
        final var reply = decode(Wire.BINARY, DaemonFixtures.REPLIES_HEX, index, FormatReply.newBuilder());
        assertThat(decode(Wire.JSON, DaemonFixtures.REPLIES_JSON, index, FormatReply.newBuilder())).isEqualTo(reply);
        assertEncodes(reply, DaemonFixtures.REPLIES_HEX.get(index), DaemonFixtures.REPLIES_JSON.get(index));
        assertThat(DaemonFixtures.violations(reply)).isEmpty();
    }

    @Test
    void aReplyWithATwoByteLengthIsTheSameReplyOnBothWires() {
        final var reply = decode(Wire.BINARY, List.of(DaemonFixtures.LARGE_REPLY_HEX), 0, FormatReply.newBuilder());
        assertThat(decode(Wire.JSON, List.of(DaemonFixtures.LARGE_REPLY_JSON), 0, FormatReply.newBuilder())).isEqualTo(reply);
        assertEncodes(reply, DaemonFixtures.LARGE_REPLY_HEX, DaemonFixtures.LARGE_REPLY_JSON);
        assertThat(DaemonFixtures.bytes(List.of(DaemonFixtures.LARGE_REPLY_HEX))[0] & 0x80).isNotZero();
    }

    @Test
    void aReplySplitAfterTheFirstByteOfItsLengthIsReadWhole() {
        final var bytes = DaemonFixtures.bytes(List.of(DaemonFixtures.LARGE_REPLY_HEX));
        final var split = new SequenceInputStream(new ByteArrayInputStream(bytes, 0, 1), new ByteArrayInputStream(bytes, 1, bytes.length - 1));
        assertThat(Wire.BINARY.frames(split).map(HexFormat.of()::formatHex).toList())
                .containsExactly(DaemonFixtures.LARGE_REPLY_HEX.substring(4));
    }

    @Test
    void onlyTheRequestFixtureWithoutASourceBreaksTheProtocolRules() {
        final var violations = requests()
                .map(index -> decode(Wire.BINARY, DaemonFixtures.REQUESTS_HEX, index, FormatRequest.newBuilder()))
                .map(DaemonFixtures::violations)
                .toList();
        assertThat(violations).containsExactly(List.of(), List.of(), List.of("value is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json}", "[]", "{\"id\":\"x\"}", "{\"unknown\":1}"})
    void aJsonLineThatIsNotARequestIsRejected(final String line) {
        assertThatThrownBy(() -> Wire.JSON.decode(line.getBytes(StandardCharsets.UTF_8), FormatRequest.newBuilder()))
                .isInstanceOf(InvalidProtocolBufferException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "01", "0d0801"})
    void aStreamThatEndsMidFrameYieldsOnlyTheWholeFrames(final String tail) {
        final var stream = DaemonFixtures.bytes(List.of(DaemonFixtures.REQUESTS_HEX.getFirst(), tail));
        assertThat(Wire.BINARY.frames(new ByteArrayInputStream(stream)).map(HexFormat.of()::formatHex).toList())
                .containsExactly(DaemonFixtures.REQUESTS_HEX.getFirst().substring(2));
    }

    private static <B extends Message.Builder> Message decode(final Wire wire, final List<String> fixture, final int index, final B builder) {
        final var bytes = wire == Wire.BINARY
                ? DaemonFixtures.bytes(List.of(fixture.get(index)))
                : DaemonFixtures.text(List.of(fixture.get(index))).getBytes(StandardCharsets.UTF_8);
        final var frame = wire.frames(new ByteArrayInputStream(bytes)).toList();
        assertThat(frame).hasSize(1);
        try {
            return wire.decode(frame.getFirst(), builder).build();
        } catch (final InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertEncodes(final Message message, final String hex, final String json) {
        assertThat(HexFormat.of().formatHex(Wire.BINARY.encode(message))).isEqualTo(hex);
        assertThat(new String(Wire.JSON.encode(message), StandardCharsets.UTF_8)).isEqualTo(json + "\n");
    }
}
