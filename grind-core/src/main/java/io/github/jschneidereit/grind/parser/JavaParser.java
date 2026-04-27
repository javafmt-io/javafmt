package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.SourcePositions;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Position;

import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Parses Java source by driving javac's parser directly via {@code com.sun.tools.javac.*}
 * internals, skipping {@code JavaCompiler.getTask} and therefore
 * {@code BasicJavacTask.initPlugins} — the {@link java.util.ServiceLoader} walk over javac's
 * URL classpath that dominated parse cost after we eliminated the boot-classpath scan in the
 * {@code StandardJavaFileManager}-reuse pass.
 *
 * <p><b>Threading.</b> A {@link Context} is cached in a {@link ThreadLocal}; each
 * thread's parser, log, trees, and interned name table live for that thread's lifetime.
 * Concurrent calls from different threads each use their own context and do not contend.
 * A single thread's calls are serialized through its context. The diagnostic listener
 * registered on the {@link Log} is a per-context indirection that delegates to a
 * {@link DiagnosticCollector} swapped in at the start of each call.
 *
 * <p><b>Module access.</b> This class references {@code com.sun.tools.javac.*} packages
 * which are only available with {@code --add-exports} for {@code jdk.compiler}'s
 * {@code api}, {@code parser}, {@code tree}, and {@code util} packages. The
 * grind-core jar declares these via the {@code Add-Exports} manifest attribute (JEP 261)
 * so consumers on the classpath inherit access automatically; module-system consumers
 * must add the exports explicitly.
 */
public final class JavaParser {

    private static final ThreadLocal<ParseContext> CONTEXTS = ThreadLocal.withInitial(ParseContext::new);

    public static CompilationUnitTree parse(final String source) {
        return parseUnit(source).tree();
    }

    public static ParsedUnit parseUnit(final String source) {
        Objects.requireNonNull(source, "source");

        final var stripped = !source.isEmpty() && source.charAt(0) == '﻿' ? source.substring(1) : source;

        final var pc = CONTEXTS.get();
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var fileObject = new InMemoryJavaFileObject(stripped);

        pc.diagnosticsRef.current = diagnostics;
        try {
            pc.log.useSource(fileObject);
            pc.log.nerrors = 0;
            pc.log.nwarnings = 0;

            final var parser = pc.factory.newParser(stripped, false, true, true);
            final JCTree.JCCompilationUnit unit = parser.parseCompilationUnit();

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

            final var positions = pc.trees.getSourcePositions();
            final var comments = CommentScanner.scan(stripped);
            return CommentAttacher.attach(unit, stripped, positions, comments);
        } finally {
            pc.diagnosticsRef.current = EMPTY_COLLECTOR;
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

    private static final DiagnosticCollector<JavaFileObject> EMPTY_COLLECTOR = new DiagnosticCollector<>();

    private static final class DiagnosticsRef implements DiagnosticListener<JavaFileObject> {

        DiagnosticCollector<JavaFileObject> current = EMPTY_COLLECTOR;

        @Override
        public void report(final javax.tools.Diagnostic<? extends JavaFileObject> diagnostic) {
            current.report(diagnostic);
        }
    }

    private static final class ParseContext {

        final DiagnosticsRef diagnosticsRef = new DiagnosticsRef();
        final ParserFactory factory;
        final Log log;
        final JavacTrees trees;

        ParseContext() {
            final var context = new Context();
            context.put(DiagnosticListener.class, diagnosticsRef);
            JavacFileManager.preRegister(context);
            this.log = Log.instance(context);
            this.factory = ParserFactory.instance(context);
            this.trees = JavacTrees.instance(context);
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

    private JavaParser() {}
}
