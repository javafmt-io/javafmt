package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class JavaParser {

    private JavaParser() {}

    public static CompilationUnitTree parse(final String source) {
        Objects.requireNonNull(source, "source");

        final var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available; run on a JDK, not a JRE");
        }

        final var diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (final var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final var fileObject = new InMemoryJavaFileObject(source);
            final var task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, null, null, List.of(fileObject));
            final var trees = task.parse();

            final var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .toList();

            if (!errors.isEmpty()) {
                final var message = errors.stream()
                        .map(d -> d.getMessage(Locale.ROOT))
                        .collect(Collectors.joining("; "));
                throw new ParseException(message, errors);
            }

            final var iterator = trees.iterator();
            if (!iterator.hasNext()) {
                throw new ParseException("Parser produced no compilation units", List.of());
            }
            return iterator.next();

        } catch (IOException e) {
            throw new ParseException("I/O error during parsing", e);
        }
    }

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {

        private final String source;

        InMemoryJavaFileObject(final String source) {
            super(URI.create("string:///Grind.java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
