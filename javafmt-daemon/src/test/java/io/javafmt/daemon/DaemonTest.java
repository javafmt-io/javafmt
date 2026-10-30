package io.javafmt.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DaemonTest {

    @Test
    void theBinaryFixtureRequestsGetTheBinaryFixtureReplies() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(DaemonFixtures.bytes(DaemonFixtures.REQUESTS_HEX)), out);
        assertThat(DaemonFixtures.hexFrames(out.toByteArray())).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_HEX);
    }

    @Test
    void theJsonFixtureRequestsGetTheJsonFixtureReplies() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.JSON, input(DaemonFixtures.text(DaemonFixtures.REQUESTS_JSON)), out);
        assertThat(out.toString(StandardCharsets.UTF_8).lines().toList()).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_JSON);
    }

    @Test
    void aMalformedFrameDoesNotKillTheLoop() {
        final var garbage = new byte[] {3, (byte) 0xff, (byte) 0xff, (byte) 0xff,};
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(concat(garbage, delimited(request(1, "class A {}")))), out);
        assertThat(replies(out)).extracting(FormatReply::getId).containsExactly(1);
    }

    @Test
    void aMalformedJsonLineDoesNotKillTheLoop() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.JSON, input("{not json}\n{\"id\":1,\"source\":\"class A {}\"}\n"), out);
        assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("{\"id\":1,");
    }

    @Test
    void aTruncatedFinalFrameIsDropped() {
        final var whole = delimited(request(1, "class A {}"));
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(concat(whole, Arrays.copyOf(whole, whole.length - 2))), out);
        assertThat(replies(out)).extracting(FormatReply::getId).containsExactly(1);
    }

    @Test
    void anErrorWhileFormattingIsHandedToTheFatalHandler() {
        final var fatal = new AtomicReference<Error>();
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(delimited(request(1, "class A {}"))),
                out,
                (id, source) -> {
                    throw new OutOfMemoryError("simulated");
                },
                fatal::set);
        assertThat(fatal.get())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated");
        assertThat(out.toByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void noMoreRequestsFormatAtOnceThanTheDaemonHasThreads(final int threads) {
        final var running = new AtomicInteger();
        final var mostAtOnce = new AtomicInteger();
        final var requests = IntStream.rangeClosed(1, 6)
                .mapToObj(id -> delimited(request(id, "class A {}")))
                .reduce(new byte[0], DaemonTest::concat);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(requests),
                out,
                threads,
                (id, source) -> {
                    mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                    running.decrementAndGet();
                    return FormatReply.newBuilder().setId(id).setOutput(source).build();
                },
                _ -> {});
        assertThat(mostAtOnce.get()).isBetween(1, threads);
        assertThat(replies(out)).hasSize(6);
    }

    @Test
    void theDefaultThreadCountIsTheCpuCountCappedAtFour() {
        assertThat(Daemon.DEFAULT_THREADS).isEqualTo(Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4));
    }

    @Test
    void deeplyNestedCodeFormatsOnTheDaemonsThreadsWhateverTheDefaultStackSize() {
        final var nested = IntStream.range(0, 600).boxed().reduce("a", (inner, level) -> "f" + level + "(" + inner + ")", (left, right) -> right);
        final var source = "class Deep {\n\n    Object v(final Object a) {\n        return " + nested + ";\n    }\n}\n";
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(delimited(request(1, source))), out, DaemonHandler::handle, _ -> {});
        assertThat(replies(out)).singleElement().satisfies(reply -> assertThat(reply.getDiagnosticsList()).isEmpty());
    }

    @Test
    void noReplyIsWrittenWhileTheFatalHandlerRuns() {
        final var fatalStarted = new CountDownLatch(1);
        final var writtenDuringFatal = new AtomicInteger(-1);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(concat(delimited(request(1, "class A {}")), delimited(request(2, "class B {}")))),
                out,
                (id, source) -> {
                    if (id == 1) {
                        throw new OutOfMemoryError("simulated");
                    }
                    awaitUninterruptibly(fatalStarted);
                    return FormatReply.newBuilder().setId(id).setOutput(source).build();
                },
                error -> {
                    fatalStarted.countDown();
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
                    writtenDuringFatal.set(out.size());
                });
        assertThat(writtenDuringFatal).hasValue(0);
        assertThat(replies(out)).extracting(FormatReply::getId).containsExactly(2);
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void aRequestThatFailsToFormatStillGetsAReplyCarryingTheFailure() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(delimited(request(1, "class A {}"))),
                out,
                (_, _) -> {
                    throw new IllegalStateException("boom");
                },
                error -> {});
        assertThat(replies(out))
                .containsExactly(
                        FormatReply.newBuilder()
                                .setId(1)
                                .setOutput("class A {}")
                                .addDiagnostics(
                                        Diagnostic.newBuilder()
                                                .setSeverity(Severity.SEVERITY_ERROR)
                                                .setKind(Kind.KIND_INTERNAL)
                                                .setMessage("IllegalStateException: boom"))
                                .build());
    }

    @Test
    void aRequestWithoutASourceGetsTheRuleItBreaksAsACliReply() {
        final var request = FormatRequest.newBuilder().setId(4).build();
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(delimited(request)), out);
        assertThat(replies(out))
                .singleElement()
                .satisfies(reply -> {
                    assertThat(reply.getDiagnosticsList())
                            .extracting(Diagnostic::getKind, Diagnostic::getMessage)
                            .containsExactlyElementsOf(
                                    DaemonFixtures.violations(request)
                                            .stream()
                                            .map(violation -> tuple(Kind.KIND_CLI, "source: " + violation))
                                            .toList());
                    assertThat(DaemonFixtures.violations(reply)).isEmpty();
                });
    }

    @Test
    void aRequestWithoutAnIdIsDropped() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(delimited(FormatRequest.newBuilder().setSource("class A {}").build())), out);
        assertThat(out.toByteArray()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Wire.class)
    void theNextRequestIsReadWhileTheCurrentOneIsStillBeingFormatted(final Wire wire) {
        final var nextRequestRead = new CountDownLatch(1);
        final var repliedBeforeReadingOn = new AtomicBoolean(false);
        final var first = new ByteArrayInputStream(wire.encode(request(1, "class A {}")));
        final var in = new InputStream() {

            @Override
            public int read() {
                final var next = first.read();
                if (next < 0) {
                    nextRequestRead.countDown();
                }
                return next;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) {
                final var read = first.read(buffer, offset, length);
                if (read < 0) {
                    nextRequestRead.countDown();
                }
                return read;
            }
        };
        final var received = new ByteArrayOutputStream();
        final var out = new OutputStream() {

            @Override
            public void write(final int b) {
                write(new byte[] {(byte) b,}, 0, 1);
            }

            @Override
            public void write(final byte[] buffer, final int offset, final int length) {
                try {
                    if (!nextRequestRead.await(5, TimeUnit.SECONDS)) {
                        repliedBeforeReadingOn.set(true);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                received.write(buffer, offset, length);
            }
        };
        Daemon.serve(wire, in, out);
        assertThat(repliedBeforeReadingOn).isFalse();
        assertThat(received.size()).isPositive();
    }

    @Test
    void everyConcurrentRequestGetsExactlyOneWholeReply() {
        final var requests = IntStream.range(1, 51)
                .mapToObj(i -> delimited(request(i, "class A" + i + " {   }")))
                .reduce(new byte[0], DaemonTest::concat);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(requests), out);
        assertThat(replies(out)).extracting(FormatReply::getId).containsExactlyInAnyOrderElementsOf(IntStream.range(1, 51).boxed().toList());
        assertThat(replies(out)).allSatisfy(reply -> assertThat(DaemonFixtures.violations(reply)).isEmpty());
    }

    private static FormatRequest request(final int id, final String source) {
        return FormatRequest.newBuilder().setId(id).setSource(source).build();
    }

    private static ByteArrayInputStream input(final String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] delimited(final FormatRequest request) {
        return Wire.BINARY.encode(request);
    }

    private static byte[] concat(final byte[] left, final byte[] right) {
        final var joined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }

    private static List<FormatReply> replies(final ByteArrayOutputStream out) {
        return DaemonFixtures.hexFrames(out.toByteArray()).stream().map(DaemonTest::reply).toList();
    }

    private static FormatReply reply(final String hexFrame) {
        try {
            return FormatReply.parseDelimitedFrom(new ByteArrayInputStream(HexFormat.of().parseHex(hexFrame)));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
