package io.javafmt.daemon;

import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class Daemon {

    public final int DEFAULT_THREADS = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);

    private final long STACK_SIZE = 64L * 1024 * 1024;

    public void serve(final Wire wire, final InputStream in, final OutputStream out) {
        serve(wire, in, out, DEFAULT_THREADS);
    }

    public void serve(final Wire wire, final InputStream in, final OutputStream out, final int threads) {
        serve(wire, in, out, threads, DaemonHandler::handle, Daemon::exit);
    }

    void serve(
            final Wire wire,
            final InputStream in,
            final OutputStream out,
            final BiFunction<Integer, String, FormatReply> handler,
            final Consumer<Error> fatal) {
        serve(wire, in, out, DEFAULT_THREADS, handler, fatal);
    }

    void serve(
            final Wire wire,
            final InputStream in,
            final OutputStream out,
            final int threads,
            final BiFunction<Integer, String, FormatReply> handler,
            final Consumer<Error> fatal) {
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, but was: " + threads);
        }
        final var replies = new Replies(out, new ReentrantLock());
        try (
                final var pool = Executors.newFixedThreadPool(
                        threads,
                        Thread.ofPlatform().name("javafmt-daemon-", 0).daemon(true).stackSize(STACK_SIZE).factory())) {
            wire.frames(in).forEach(frame -> pool.execute(() -> respond(wire, frame, replies, handler, fatal)));
        }
    }

    private void respond(
            final Wire wire,
            final byte[] frame,
            final Replies replies,
            final BiFunction<Integer, String, FormatReply> handler,
            final Consumer<Error> fatal) {
        try {
            reply(wire, frame, handler).map(wire::encode).ifPresent(replies::write);
        } catch (final RuntimeException e) {
            log.warn("dropped a reply that could not be written", e);
        } catch (final Error e) {
            replies.failWith(fatal, e);
        }
    }

    private Optional<FormatReply> reply(final Wire wire, final byte[] frame, final BiFunction<Integer, String, FormatReply> handler) {
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
        return Optional.of(answer(request, handler));
    }

    private FormatReply answer(final FormatRequest request, final BiFunction<Integer, String, FormatReply> handler) {
        if (!request.hasSource()) {
            return DaemonHandler.failure(request.getId(), "", Kind.KIND_CLI, "source: value is required");
        }
        try {
            return handler.apply(request.getId(), request.getSource());
        } catch (final RuntimeException e) {
            return DaemonHandler.failure(
                    request.getId(),
                    request.getSource(),
                    Kind.KIND_INTERNAL,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void exit(final Error error) {
        log.error("javafmt daemon failed, exiting", error);
        System.exit(1);
    }

    private record Replies(OutputStream out, Lock lock) {

        void write(final byte[] reply) {
            lock.lock();
            try {
                out.write(reply);
                out.flush();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }

        void failWith(final Consumer<Error> fatal, final Error error) {
            lock.lock();
            try {
                fatal.accept(error);
            } finally {
                lock.unlock();
            }
        }
    }
}
