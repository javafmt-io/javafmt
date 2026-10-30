package io.javafmt.intellij;

import build.buf.validate.ValidateProto;
import com.google.protobuf.Message;
import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.FormatReply;
import io.javafmt.daemon.protocol.FormatRequest;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FakeDaemon {

    static final Map<String, FormatReply> CANNED = Map.of(
            "class Foo { int x; }",
            reply("class Foo {\n\n    int x;\n}\n"),
            "class A { int x; }",
            reply("class A {\n\n    int x;\n}\n"),
            "class B { int x; }",
            reply("class B {\n\n    int x;\n}\n"),
            "class Warned { int x; }",
            reply(
                    "class Warned {\n\n    int x;\n}\n",
                    diagnostic(Severity.SEVERITY_WARNING, Kind.KIND_WARNING, "utility class has no private constructor", 1, 1)),
            "class Broken { int x = ; }",
            reply("class Broken { int x = ; }", diagnostic(Severity.SEVERITY_ERROR, Kind.KIND_PARSE, "expected an expression", 1, 24)),
            "class Internal {}",
            reply("class Internal {}", diagnostic(Severity.SEVERITY_ERROR, Kind.KIND_INTERNAL, "dropped token", 0, 0)));

    static Diagnostic diagnostic(final Severity severity, final Kind kind, final String message, final int line, final int column) {
        return Diagnostic.newBuilder()
                .setSeverity(severity)
                .setKind(kind)
                .setMessage(message)
                .setLine(line)
                .setColumn(column)
                .build();
    }

    public static void main(final String[] args) {
        final var in = new BufferedInputStream(System.in);
        Stream.generate(() -> read(in))
                .takeWhile(Optional::isPresent)
                .map(Optional::orElseThrow)
                .forEach(request -> write(answer(request), System.out));
    }

    static Path script(final Path dir, final String name) throws IOException {
        final var java = ProcessHandle.current().info().command().orElseThrow();
        final var classpath = Stream.of(FakeDaemon.class, FormatReply.class, Message.class, ValidateProto.class)
                .map(FakeDaemon::location)
                .distinct()
                .collect(Collectors.joining(File.pathSeparator));
        final var path = dir.resolve(name);
        Files.writeString(
                path,
                "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then\n    echo 'javafmt 0.2.0'\n    exit 0\nfi\nexec '" + java + "' -cp '" + classpath
                        + "' "
                        + FakeDaemon.class.getName()
                        + "\n");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }

    private static FormatReply answer(final FormatRequest request) {
        return CANNED.getOrDefault(request.getSource(), reply(request.getSource()))
                .toBuilder()
                .setId(request.getId())
                .build();
    }

    private static FormatReply reply(final String output, final Diagnostic... diagnostics) {
        return FormatReply.newBuilder().setOutput(output).addAllDiagnostics(List.of(diagnostics)).build();
    }

    private static Optional<FormatRequest> read(final InputStream in) {
        try {
            return Optional.ofNullable(FormatRequest.parseDelimitedFrom(in));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(final FormatReply reply, final OutputStream out) {
        try {
            reply.writeDelimitedTo(out);
            out.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String location(final Class<?> type) {
        final var resource = "/" + type.getName().replace('.', '/') + ".class";
        final var url = Objects.requireNonNull(type.getResource(resource), resource).toString();
        final var root = url.startsWith("jar:")
                ? url.substring("jar:".length(), url.indexOf("!/"))
                : url.substring(0, url.length() - resource.length());
        return Path.of(URI.create(root)).toString();
    }

    private FakeDaemon() {}
}
