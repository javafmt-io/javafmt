package io.javafmt.daemon;

import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@UtilityClass
public final class Daemon {

    public final int DEFAULT_THREADS = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);

    private final long STACK_SIZE = 64L * 1024 * 1024;

    public void serve(final Wire wire, final InputStream in, final OutputStream out, final int threads) {
        serve(wire, in, out, threads, DaemonHandler::handle, Daemon::exit);
    }

    void serve(final Wire wire,
               final InputStream in,
               final OutputStream out,
               final int threads,
               final Function<FormatRequest, FormatReply> handler,
               final Consumer<Error> fatal) {
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, but was: " + threads);
        }
        final var connection = new Connection(wire, out, handler, fatal);
        try (final var pool = Executors.newFixedThreadPool(threads, workerThreads())) {
            wire.frames(in).forEach(frame -> pool.execute(() -> connection.respond(frame)));
        }
    }

    private ThreadFactory workerThreads() {
        return Thread.ofPlatform()
                .name("javafmt-daemon-", 0)
                .daemon(true)
                .stackSize(STACK_SIZE)
                .factory();
    }

    private void exit(final Error error) {
        log.error("javafmt daemon failed, exiting", error);
        System.exit(1);
    }

    @RequiredArgsConstructor
    private static final class Connection {

        private final Wire wire;

        private final OutputStream out;

        private final Function<FormatRequest, FormatReply> handler;

        private final Consumer<Error> fatal;

        void respond(final byte[] frame) {
            try {
                reply(frame).map(wire::encode).ifPresent(this::write);
            } catch (final RuntimeException e) {
                log.warn("dropped a reply that could not be written", e);
            } catch (final Error e) {
                failWith(e);
            }
        }

        private Optional<FormatReply> reply(final byte[] frame) {
            final FormatRequest request;
            try {
                request = wire.decode(frame, FormatRequest.newBuilder()).build();
            } catch (final IOException | RuntimeException e) {
                log.warn("dropped a malformed request", e);
                return Optional.empty();
            }
            if (request.getId() == 0) {
                log.warn("dropped a request without an id");
                return Optional.empty();
            }
            return Optional.of(answer(request));
        }

        private FormatReply answer(final FormatRequest request) {
            if (!request.hasSource()) {
                return DaemonHandler.failure(request.getId(), "", Kind.KIND_CLI, "source: value is required");
            }
            try {
                return handler.apply(request);
            } catch (final RuntimeException e) {
                return DaemonHandler.failure(
                        request.getId(),
                        request.getSource(),
                        Kind.KIND_INTERNAL,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private synchronized void write(final byte[] reply) {
            try {
                out.write(reply);
                out.flush();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private synchronized void failWith(final Error error) {
            fatal.accept(error);
        }
    }
}
