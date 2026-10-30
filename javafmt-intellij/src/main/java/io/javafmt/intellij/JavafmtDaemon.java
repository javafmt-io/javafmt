package io.javafmt.intellij;

import com.google.protobuf.InvalidProtocolBufferException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.diagnostic.Logger;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class JavafmtDaemon {

    private static final Logger LOG = Logger.getInstance(JavafmtDaemon.class);

    private final Process process;

    private final Duration timeout;

    private final Consumer<Process> kill;

    private final Map<Integer, CompletableFuture<JavafmtBinary.Outcome>> pending = new ConcurrentHashMap<>();

    private final BlockingQueue<FormatRequest> requests = new LinkedBlockingQueue<>();

    private final AtomicInteger ids = new AtomicInteger();

    private final AtomicReference<Optional<JavafmtBinary.Outcome>> stopped = new AtomicReference<>(Optional.empty());

    private final FutureTask<Void> writer;

    private JavafmtDaemon(final Process process, final Duration timeout, final Consumer<Process> kill) {
        this.process = process;
        this.timeout = timeout;
        this.kill = kill;
        this.writer = new FutureTask<>(this::writeRequests, null);
    }

    static JavafmtDaemon start(final Path executable, final Duration timeout) throws com.intellij.execution.ExecutionException {
        return start(executable, timeout, OSProcessUtil::killProcessTree);
    }

    static JavafmtDaemon start(
            final Path executable,
            final Duration timeout,
            final Consumer<Process> kill)
            throws com.intellij.execution.ExecutionException {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(kill, "kill");
        final var daemon = new JavafmtDaemon(new GeneralCommandLine(executable.toString(), "--daemon").createProcess(), timeout, kill);
        ProcessIOExecutorService.INSTANCE.execute(daemon.writer);
        ProcessIOExecutorService.INSTANCE.execute(daemon::readReplies);
        ProcessIOExecutorService.INSTANCE.execute(daemon::discardStderr);
        return daemon;
    }

    boolean isAlive() {
        return stopped.get().isEmpty();
    }

    JavafmtBinary.Outcome format(final String source) {
        Objects.requireNonNull(source, "source");
        final var id = ids.incrementAndGet();
        final var reply = new CompletableFuture<JavafmtBinary.Outcome>();
        pending.put(id, reply);
        try {
            final var ended = stopped.get();
            if (ended.isPresent()) {
                return ended.get();
            }
            requests.add(FormatRequest.newBuilder().setId(id).setSource(source).build());
            return reply.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            final var refused = refused("javafmt did not respond within " + timeout.toSeconds() + "s");
            stop(refused);
            return refused;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JavafmtBinary.Outcome.Cancelled();
        } catch (final ExecutionException e) {
            return refused("javafmt failed: " + e.getCause());
        } finally {
            pending.remove(id);
        }
    }

    void stop() {
        stop(new JavafmtBinary.Outcome.Cancelled());
    }

    private static JavafmtBinary.Outcome refused(final String reason) {
        return new JavafmtBinary.Outcome.Refused(reason, true);
    }

    private void stop(final JavafmtBinary.Outcome outcome) {
        if (!stopped.compareAndSet(Optional.empty(), Optional.of(outcome))) {
            return;
        }
        pending.values().forEach(reply -> reply.complete(outcome));
        writer.cancel(true);
        if (process.isAlive()) {
            ProcessIOExecutorService.INSTANCE.execute(() -> kill.accept(process));
        }
    }

    private void writeRequests() {
        try (final var stdin = new BufferedOutputStream(process.getOutputStream())) {
            while (isAlive()) {
                requests.take().writeDelimitedTo(stdin);
                stdin.flush();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            stop(refused(exitReason("javafmt stopped reading its input")));
        }
    }

    private void readReplies() {
        try (final var stdout = new BufferedInputStream(process.getInputStream())) {
            Stream.generate(() -> nextReply(stdout))
                    .takeWhile(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .forEach(this::accept);
        } catch (final IOException e) {
            LOG.debug("javafmt daemon output closed", e);
        }
        if (isAlive()) {
            stop(refused(exitReason("javafmt closed its output")));
        }
    }

    private Optional<FormatReply> nextReply(final InputStream stdout) {
        try {
            return Optional.ofNullable(FormatReply.parseDelimitedFrom(stdout));
        } catch (final InvalidProtocolBufferException e) {
            stop(refused("javafmt sent a reply that is not part of its protocol"));
            return Optional.empty();
        } catch (final IOException e) {
            LOG.debug("javafmt daemon output closed", e);
            return Optional.empty();
        }
    }

    private void accept(final FormatReply reply) {
        Optional.ofNullable(pending.get(reply.getId()))
                .ifPresent(waiting -> waiting.complete(new JavafmtBinary.Outcome.Formatted(reply.getOutput(), reply.getDiagnosticsList())));
    }

    private String exitReason(final String stillRunning) {
        try {
            if (process.waitFor(1, TimeUnit.SECONDS)) {
                return "javafmt exited with code " + process.exitValue();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return stillRunning;
    }

    private void discardStderr() {
        try (final var stderr = process.getErrorStream()) {
            stderr.transferTo(OutputStream.nullOutputStream());
        } catch (final IOException e) {
            LOG.debug("javafmt daemon error output closed", e);
        }
    }
}
