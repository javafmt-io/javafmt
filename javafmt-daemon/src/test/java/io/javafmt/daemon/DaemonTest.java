package io.javafmt.daemon;

import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class DaemonTest {

    @Test
    void theBinaryFixtureRequestsGetTheBinaryFixtureReplies() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(DaemonFixtures.bytes(DaemonFixtures.REQUESTS_HEX)), out, Daemon.DEFAULT_THREADS);
        assertThat(DaemonFixtures.hexFrames(out.toByteArray())).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_HEX);
    }

    @Test
    void theJsonFixtureRequestsGetTheJsonFixtureReplies() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.JSON, input(DaemonFixtures.text(DaemonFixtures.REQUESTS_JSON)), out, Daemon.DEFAULT_THREADS);
        assertThat(out.toString(StandardCharsets.UTF_8).lines().toList()).containsExactlyInAnyOrderElementsOf(DaemonFixtures.REPLIES_JSON);
    }

    @Test
    void aMalformedFrameDoesNotKillTheLoop() {
        final var garbage = new byte[]{3, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(concat(garbage, delimited(DaemonFixtures.request(1, "class A {}")))),
                out,
                Daemon.DEFAULT_THREADS);
        assertThat(replies(out))
                .extracting(FormatReply::getId)
                .containsExactly(1);
    }

    @Test
    void aJsonRequestCarriesItsClasspath() {
        final var out = new ByteArrayOutputStream();
        final var source = "import java.util.*;\n\nclass Order {\n    List<String> lines;\n}\n";
        final var request = DaemonFixtures.request(1, source).toBuilder()
                .setClasspath("")
                .build();
        Daemon.serve(Wire.JSON, new ByteArrayInputStream(Wire.JSON.encode(request)), out, Daemon.DEFAULT_THREADS);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("import java.util.List;");
    }

    @Test
    void aMalformedJsonLineDoesNotKillTheLoop() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.JSON, input("{not json}\n{\"id\":1,\"source\":\"class A {}\"}\n"), out, Daemon.DEFAULT_THREADS);
        assertThat(out.toString(StandardCharsets.UTF_8).lines())
                .singleElement(InstanceOfAssertFactories.STRING)
                .startsWith("{\"id\":1,");
    }

    @Test
    void aTruncatedFinalFrameIsDropped() {
        final var whole = delimited(DaemonFixtures.request(1, "class A {}"));
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(concat(whole, Arrays.copyOf(whole, whole.length - 2))), out, Daemon.DEFAULT_THREADS);
        assertThat(replies(out))
                .extracting(FormatReply::getId)
                .containsExactly(1);
    }

    @Test
    void anErrorWhileFormattingIsHandedToTheFatalHandler() {
        final var fatal = new AtomicReference<Error>();
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(delimited(DaemonFixtures.request(1, "class A {}"))),
                out,
                Daemon.DEFAULT_THREADS,
                _ -> {
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
                .mapToObj(id -> delimited(DaemonFixtures.request(id, "class A {}")))
                .reduce(new byte[0], DaemonTest::concat);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(requests),
                out,
                threads,
                request -> {
                    mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                    running.decrementAndGet();
                    return FormatReply.newBuilder()
                            .setId(request.getId())
                            .setOutput(request.getSource())
                            .build();
                },
                _ -> {});
        assertThat(mostAtOnce.get()).isBetween(1, threads);
        assertThat(replies(out)).hasSize(6);
    }

    @Test
    void theDefaultThreadCountIsTheCpuCountCappedAtFour() {
        assertThat(Daemon.DEFAULT_THREADS)
                .isBetween(1, 4)
                .isLessThanOrEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    void aZeroThreadCountIsRejected() {
        final var out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> Daemon.serve(Wire.BINARY, new ByteArrayInputStream(new byte[0]), out, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("threads must be positive, but was: 0");
    }

    @Test
    void deeplyNestedCodeFormatsOnTheDaemonsThreadsWhateverTheDefaultStackSize() {
        final var nested = IntStream.range(0, 600)
                .boxed()
                .reduce("a", (inner, level) -> "f" + level + "(" + inner + ")", (_, right) -> right);
        final var source = "class Deep {\n    Object v(final Object a) {\n        return " + nested + ";\n    }\n}\n";
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(delimited(DaemonFixtures.request(1, source))),
                out,
                Daemon.DEFAULT_THREADS,
                DaemonHandler::handle,
                _ -> {});
        assertThat(replies(out))
                .singleElement()
                .satisfies(reply -> assertThat(reply.getDiagnosticsList()).isEmpty());
    }

    @Test
    void noReplyIsWrittenWhileTheFatalHandlerRuns() {
        final var fatalStarted = new CountDownLatch(1);
        final var writtenDuringFatal = new AtomicInteger(-1);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(
                        concat(delimited(DaemonFixtures.request(1, "class A {}")), delimited(DaemonFixtures.request(2, "class B {}")))),
                out,
                Daemon.DEFAULT_THREADS,
                request -> {
                    if (request.getId() == 1) {
                        throw new OutOfMemoryError("simulated");
                    }
                    awaitUninterruptibly(fatalStarted);
                    return FormatReply.newBuilder()
                            .setId(request.getId())
                            .setOutput(request.getSource())
                            .build();
                },
                _ -> {
                    fatalStarted.countDown();
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
                    writtenDuringFatal.set(out.size());
                });
        assertThat(writtenDuringFatal).hasValue(0);
        assertThat(replies(out))
                .extracting(FormatReply::getId)
                .containsExactly(2);
    }

    @Test
    void aRequestThatFailsToFormatStillGetsAReplyCarryingTheFailure() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(delimited(DaemonFixtures.request(1, "class A {}"))),
                out,
                Daemon.DEFAULT_THREADS,
                _ -> {
                    throw new IllegalStateException("boom");
                },
                _ -> {});
        assertThat(replies(out))
                .containsExactly(FormatReply.newBuilder()
                        .setId(1)
                        .setOutput("class A {}")
                        .addDiagnostics(Diagnostic.newBuilder()
                                .setSeverity(Severity.SEVERITY_ERROR)
                                .setKind(Kind.KIND_INTERNAL)
                                .setMessage("IllegalStateException: boom"))
                        .build());
    }

    @Test
    void aRequestWithoutASourceGetsTheRuleItBreaksAsACliReply() {
        final var request = FormatRequest.newBuilder()
                .setId(4)
                .build();
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(delimited(request)), out, Daemon.DEFAULT_THREADS);
        assertThat(replies(out)).singleElement().satisfies(reply -> {
            assertThat(reply.getDiagnosticsList())
                    .extracting(Diagnostic::getKind, Diagnostic::getMessage)
                    .containsExactlyElementsOf(DaemonFixtures.violations(request)
                            .stream()
                            .map(violation -> tuple(Kind.KIND_CLI, "source: " + violation))
                            .toList());
            assertThat(DaemonFixtures.violations(reply)).isEmpty();
        });
    }

    @Test
    void aRequestWithoutAnIdIsDropped() {
        final var out = new ByteArrayOutputStream();
        Daemon.serve(
                Wire.BINARY,
                new ByteArrayInputStream(
                        delimited(FormatRequest.newBuilder()
                                .setSource("class A {}")
                                .build())),
                out,
                Daemon.DEFAULT_THREADS);
        assertThat(out.toByteArray()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Wire.class)
    void theNextRequestIsReadWhileTheCurrentOneIsStillBeingFormatted(final Wire wire) {
        final var nextRequestRead = new CountDownLatch(1);
        final var repliedBeforeReadingOn = new AtomicBoolean(false);
        final var endOfInput = new InputStream() {
            @Override
            public int read() {
                nextRequestRead.countDown();
                return -1;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) {
                return read();
            }
        };
        final var in = new SequenceInputStream(new ByteArrayInputStream(wire.encode(DaemonFixtures.request(1, "class A {}"))), endOfInput);
        final var received = new ByteArrayOutputStream();
        final var out = new OutputStream() {
            @Override
            public void write(final int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(final byte[] buffer, final int offset, final int length) {
                try {
                    if (!nextRequestRead.await(5, TimeUnit.SECONDS)) {
                        repliedBeforeReadingOn.set(true);
                    }
                } catch (final InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
                received.write(buffer, offset, length);
            }
        };
        Daemon.serve(wire, in, out, Daemon.DEFAULT_THREADS);
        assertThat(repliedBeforeReadingOn).isFalse();
        assertThat(received.size()).isPositive();
    }

    @Test
    void everyConcurrentRequestGetsExactlyOneWholeReply() {
        final var requests = IntStream.range(1, 51)
                .mapToObj(i -> delimited(DaemonFixtures.request(i, "class A" + i + " {   }")))
                .reduce(new byte[0], DaemonTest::concat);
        final var out = new ByteArrayOutputStream();
        Daemon.serve(Wire.BINARY, new ByteArrayInputStream(requests), out, Daemon.DEFAULT_THREADS);
        assertThat(replies(out))
                .extracting(FormatReply::getId)
                .containsExactlyInAnyOrderElementsOf(IntStream.range(1, 51)
                        .boxed()
                        .toList());
        assertThat(replies(out)).allSatisfy(reply -> assertThat(DaemonFixtures.violations(reply)).isEmpty());
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException _) {
            Thread.currentThread().interrupt();
        }
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
        final var in = new ByteArrayInputStream(out.toByteArray());
        return Stream.generate(() -> nextReply(in))
                .takeWhile(Optional::isPresent)
                .map(Optional::orElseThrow)
                .toList();
    }

    private static Optional<FormatReply> nextReply(final InputStream in) {
        try {
            return Optional.ofNullable(FormatReply.parseDelimitedFrom(in));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
