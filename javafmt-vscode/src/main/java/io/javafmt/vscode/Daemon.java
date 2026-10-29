package io.javafmt.vscode;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class Daemon {

    private final int WORKERS = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);

    public void main(final String[] args) throws IOException {
        serve(new BufferedReader(new InputStreamReader(System.in, UTF_8)), new PrintWriter(new OutputStreamWriter(System.out, UTF_8), true));
    }

    void serve(final BufferedReader in, final PrintWriter out) {
        serve(in, out, DaemonHandler::handle, Daemon::exit);
    }

    void serve(final BufferedReader in, final PrintWriter out, final Function<FormatRequest, FormatResponse> handler, final Consumer<Error> fatal) {
        try (final var pool = Executors.newFixedThreadPool(WORKERS, Thread.ofPlatform().name("javafmt-daemon-", 0).daemon(true).factory())) {
            in.lines().forEach(line -> pool.execute(() -> respond(line, out, handler, fatal)));
        }
    }

    private void respond(
            final String line,
            final PrintWriter out,
            final Function<FormatRequest, FormatResponse> handler,
            final Consumer<Error> fatal) {
        try {
            final var reply = Json.serialize(handler.apply(Json.parse(line, FormatRequest.class)));
            synchronized (out) {
                out.println(reply);
            }
        } catch (final RuntimeException e) {
            log.warn("dropped malformed request line", e);
        } catch (final Error e) {
            fatal.accept(e);
        }
    }

    private void exit(final Error error) {
        log.error("javafmt daemon failed, exiting", error);
        System.exit(1);
    }
}
