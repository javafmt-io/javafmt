package io.javafmt.daemon;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class WireTest {

    static Stream<Arguments> requests() {
        return pairs(DaemonFixtures.REQUESTS_HEX, DaemonFixtures.REQUESTS_JSON);
    }

    static Stream<Arguments> replies() {
        return pairs(DaemonFixtures.REPLIES_HEX, DaemonFixtures.REPLIES_JSON);
    }

    @ParameterizedTest
    @MethodSource("requests")
    void eachRequestFixtureIsTheSameRequestOnBothWires(final String hex, final String json) {
        final var request = decode(Wire.BINARY, hex, FormatRequest.newBuilder());
        assertThat(decode(Wire.JSON, json, FormatRequest.newBuilder())).isEqualTo(request);
        assertEncodes(request, hex, json);
    }

    @ParameterizedTest
    @MethodSource("replies")
    void eachReplyFixtureIsTheSameReplyOnBothWires(final String hex, final String json) {
        final var reply = decode(Wire.BINARY, hex, FormatReply.newBuilder());
        assertThat(decode(Wire.JSON, json, FormatReply.newBuilder())).isEqualTo(reply);
        assertEncodes(reply, hex, json);
        assertThat(DaemonFixtures.violations(reply)).isEmpty();
    }

    @Test
    void aReplyWithATwoByteLengthIsTheSameReplyOnBothWires() {
        final var reply = decode(Wire.BINARY, DaemonFixtures.LARGE_REPLY_HEX, FormatReply.newBuilder());
        assertThat(decode(Wire.JSON, DaemonFixtures.LARGE_REPLY_JSON, FormatReply.newBuilder()))
                .isEqualTo(reply);
        assertEncodes(reply, DaemonFixtures.LARGE_REPLY_HEX, DaemonFixtures.LARGE_REPLY_JSON);
        assertThat(HexFormat.of().parseHex(DaemonFixtures.LARGE_REPLY_HEX)[0] & 0x80)
                .isNotZero();
    }

    @Test
    void aReplySplitAfterTheFirstByteOfItsLengthIsReadWhole() {
        final var bytes = HexFormat.of().parseHex(DaemonFixtures.LARGE_REPLY_HEX);
        final var split = new SequenceInputStream(new ByteArrayInputStream(bytes, 0, 1), new ByteArrayInputStream(bytes, 1, bytes.length - 1));
        assertThat(
                        Wire.BINARY.frames(split)
                                .map(HexFormat.of()::formatHex)
                                .toList())
                .containsExactly(DaemonFixtures.LARGE_REPLY_HEX.substring(4));
    }

    @Test
    void onlyTheRequestFixtureWithoutASourceBreaksTheProtocolRules() {
        final var violations = DaemonFixtures.REQUESTS_HEX.stream()
                .map(hex -> decode(Wire.BINARY, hex, FormatRequest.newBuilder()))
                .map(DaemonFixtures::violations)
                .toList();
        assertThat(violations).containsExactly(List.of(), List.of(), List.of("value is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json}", "[]", "{\"id\":\"x\"}", "{\"unknown\":1}"})
    void aJsonLineThatIsNotARequestIsRejected(final String line) {
        assertThatThrownBy(() -> Wire.JSON.decode(line.getBytes(StandardCharsets.UTF_8), FormatRequest.newBuilder()))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(InvalidProtocolBufferException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "01", "0d0801"})
    void aStreamThatEndsMidFrameYieldsOnlyTheWholeFrames(final String tail) {
        final var stream = DaemonFixtures.bytes(List.of(DaemonFixtures.REQUESTS_HEX.getFirst(), tail));
        assertThat(
                        Wire.BINARY.frames(new ByteArrayInputStream(stream))
                                .map(HexFormat.of()::formatHex)
                                .toList())
                .containsExactly(DaemonFixtures.REQUESTS_HEX.getFirst().substring(2));
    }

    private static Stream<Arguments> pairs(final List<String> hex, final List<String> json) {
        return IntStream.range(0, hex.size()).mapToObj(index -> arguments(hex.get(index), json.get(index)));
    }

    private static Message decode(final Wire wire, final String line, final Message.Builder builder) {
        final var bytes = wire == Wire.BINARY ? HexFormat.of().parseHex(line) : (line + "\n").getBytes(StandardCharsets.UTF_8);
        final var frames = wire.frames(new ByteArrayInputStream(bytes)).toList();
        assertThat(frames).hasSize(1);
        return wire.decode(frames.getFirst(), builder).build();
    }

    private static void assertEncodes(final Message message, final String hex, final String json) {
        assertThat(HexFormat.of().formatHex(Wire.BINARY.encode(message))).isEqualTo(hex);
        assertThat(new String(Wire.JSON.encode(message), StandardCharsets.UTF_8))
                .isEqualTo(json + "\n");
    }
}
