package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Position;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class JavaParser {

    public static CompilationUnitTree parse(final String source) {
        return parseUnit(source).tree();
    }

    public static ParsedUnit parseUnit(final String source) {
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
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                    .toList();

            if (!errors.isEmpty()) {
                final var message = errors.stream()
                        .map(d -> d.getMessage(Locale.ROOT))
                        .collect(Collectors.joining("; "));
                final var asDiagnostics = errors.stream()
                        .<Diagnostic>map(d -> new Diagnostic.ParseError(d.getMessage(Locale.ROOT), positionOf(d)))
                        .toList();
                throw new ParseException(message, asDiagnostics);
            }

            final var iterator = trees.iterator();
            if (!iterator.hasNext()) {
                throw new ParseException("Parser produced no compilation units", List.of());
            }
            final var unit = iterator.next();
            final var positions = Trees.instance(task).getSourcePositions();
            final var comments = CommentScanner.scan(source);
            return CommentAttacher.attach(unit, source, positions, comments);

        } catch (IOException e) {
            throw new ParseException("I/O error during parsing", e);
        }
    }

    private static Position positionOf(final javax.tools.Diagnostic<? extends JavaFileObject> d) {
        final var offset = d.getStartPosition();
        final var line = d.getLineNumber();
        final var column = d.getColumnNumber();
        if (offset == javax.tools.Diagnostic.NOPOS) {
            return Position.UNKNOWN;
        }
        return new Position(
            line == javax.tools.Diagnostic.NOPOS ? 0 : (int) line,
            column == javax.tools.Diagnostic.NOPOS ? 0 : (int) column,
            (int) offset);
    }

    static Position positionOf(final SourcePositions positions, final CompilationUnitTree unit, final com.sun.source.tree.Tree node) {
        final var offset = positions.getStartPosition(unit, node);
        if (offset < 0) {
            return Position.UNKNOWN;
        }
        final var lineMap = unit.getLineMap();
        final var line = (int) lineMap.getLineNumber(offset);
        final var column = (int) lineMap.getColumnNumber(offset);
        return new Position(line, column, (int) offset);
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

    private JavaParser() {}
}
