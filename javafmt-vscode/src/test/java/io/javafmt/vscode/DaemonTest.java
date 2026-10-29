package io.javafmt.vscode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DaemonTest {

    @Test
    void malformedLineDoesNotKillTheLoop() {
        final var in = new BufferedReader(new StringReader("{not json}\n{\"id\":\"1\",\"source\":\"class A {}\"}\n"));
        final var out = new StringWriter();
        Daemon.serve(in, new PrintWriter(out, true));
        assertThat(out.toString()).contains("\"id\":\"1\"");
    }

    @Test
    void anErrorWhileFormattingIsHandedToTheFatalHandler() {
        final var fatal = new AtomicReference<Error>();
        final var out = new StringWriter();
        Daemon.serve(
                new BufferedReader(new StringReader("{\"id\":\"1\",\"source\":\"class A {}\"}\n")),
                new PrintWriter(out, true),
                request -> {
                    throw new OutOfMemoryError("simulated");
                },
                fatal::set);
        assertThat(fatal.get())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void theNextRequestIsReadWhileTheCurrentOneIsStillBeingFormatted() {
        final var nextLineRequested = new CountDownLatch(1);
        final var repliedBeforeReadingOn = new AtomicBoolean(false);
        final var in = new Reader() {

            private boolean served;

            @Override
            public int read(final char[] buffer, final int offset, final int length) {
                if (served) {
                    nextLineRequested.countDown();
                    return -1;
                }
                served = true;
                final var line = "{\"id\":\"1\",\"source\":\"class A {}\"}\n";
                line.getChars(0, line.length(), buffer, offset);
                return line.length();
            }

            @Override
            public void close() {}
        };
        final var received = new StringWriter();
        final var out = new Writer() {

            @Override
            public void write(final char[] buffer, final int offset, final int length) {
                try {
                    if (!nextLineRequested.await(5, TimeUnit.SECONDS)) {
                        repliedBeforeReadingOn.set(true);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                received.write(buffer, offset, length);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Daemon.serve(new BufferedReader(in), new PrintWriter(out, true));
        assertThat(repliedBeforeReadingOn).isFalse();
        assertThat(received.toString()).contains("\"id\":\"1\"");
    }

    @Test
    void everyConcurrentRequestGetsExactlyOneWholeReply() {
        final var requests = IntStream.range(0, 50)
                .mapToObj(i -> "{\"id\":\"" + i + "\",\"source\":\"class A" + i + " {   }\"}\n")
                .collect(Collectors.joining());
        final var out = new StringWriter();
        Daemon.serve(new BufferedReader(new StringReader(requests)), new PrintWriter(out, true));
        final var replies = out.toString().lines().toList();
        assertThat(replies).hasSize(50);
        assertThat(replies.stream().map(reply -> Json.parse(reply, FormatResponse.class).id()).toList())
                .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 50).mapToObj(String::valueOf).toList());
    }
}
