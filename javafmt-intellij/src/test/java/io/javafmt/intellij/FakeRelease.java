package io.javafmt.intellij;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

final class FakeRelease implements AutoCloseable {

    static final String VERSION = "1.2.3";

    private static final String PREFIX = "/download/v" + VERSION + "/";

    private final HttpServer server;

    private final Map<String, byte[]> assets;

    private final Responder responder;

    private final AtomicInteger requests = new AtomicInteger();

    private FakeRelease(final Map<String, byte[]> assets, final Responder responder) throws IOException {
        this.assets = Map.copyOf(assets);
        this.responder = responder;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    static FakeRelease serving(final Map<String, byte[]> assets) throws IOException {
        return new FakeRelease(assets, FakeRelease::sendAll);
    }

    static FakeRelease servingAfter(final CountDownLatch go, final Map<String, byte[]> assets) throws IOException {
        return new FakeRelease(
                assets,
                (exchange, body) -> {
                    await(go);
                    sendAll(exchange, body);
                });
    }

    static FakeRelease tricklingAfterHalf(final CountDownLatch halfway, final Map<String, byte[]> assets) throws IOException {
        return new FakeRelease(assets, (exchange, body) -> trickle(exchange, body, halfway));
    }

    static URI unreachable() throws IOException {
        try (final var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort() + PREFIX);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + PREFIX);
    }

    int requests() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(final HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        final var path = exchange.getRequestURI().getPath();
        final var body = path.startsWith(PREFIX) ? Optional.ofNullable(assets.get(path.substring(PREFIX.length()))) : Optional.<byte[]>empty();
        try (exchange) {
            if (body.isEmpty()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            responder.respond(exchange, body.get());
        }
    }

    private static void sendAll(final HttpExchange exchange, final byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void trickle(final HttpExchange exchange, final byte[] body, final CountDownLatch halfway) throws IOException {
        final var half = body.length / 2;
        exchange.sendResponseHeaders(200, body.length);
        final var out = exchange.getResponseBody();
        out.write(body, 0, half);
        out.flush();
        halfway.countDown();
        IntStream.range(half, body.length)
                .forEach(i -> {
                    try {
                        Thread.sleep(20);
                        out.write(body[i]);
                        out.flush();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Responder {

        void respond(HttpExchange exchange, byte[] body) throws IOException;
    }
}
