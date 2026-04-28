package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.Position;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Parses Java source via the JDK's {@code javax.tools} compiler API.
 *
 * <p><b>Threading.</b> A {@link StandardJavaFileManager} is cached in a {@link ThreadLocal}
 * and lives for the lifetime of the thread that first calls {@link #parseUnit}; it is
 * never closed. This avoids re-paying javac's boot-classpath scan and {@code JarFile}
 * inflation on every call. Concurrent calls from different threads each use their own
 * file manager and do not contend; a single thread's calls are serialized through its
 * file manager but that's the normal case (one source per thread). Process-cached:
 * the {@link JavaCompiler} and each thread's {@link StandardJavaFileManager}. Per-call:
 * the {@link com.sun.source.util.JavacTask}, its diagnostic collector, and the in-memory
 * {@link JavaFileObject}.
 *
 * <p>Annotation processing is disabled ({@code -proc:none}). This implementation uses
 * only exported {@code javax.tools} and {@code com.sun.source} APIs, so consumers do
 * not need {@code --add-exports} to use it.
 *
 * <p><b>Tradeoff (perf vs. distribution).</b> An earlier revision bypassed
 * {@code JavaCompiler.getTask} and drove the parser via {@code com.sun.tools.javac.*}
 * internals (per-thread {@code Context} caching {@code ParserFactory}, {@code Log}, and
 * the interned name table; skipped {@code BasicJavacTask.initPlugins}'s per-call
 * {@link java.util.ServiceLoader} walk). That was faster, but every consumer JVM —
 * the Gradle daemon hosting {@code grind-spotless}, Maven's forked process for
 * {@code grind-maven-plugin}, and IntelliJ for {@code grind-intellij} — would have
 * needed matching {@code --add-exports} flags. The {@code Add-Exports} JAR-manifest
 * attribute (JEP 261) only fires for {@code java -jar} launches, not classpath
 * consumption, so there is no transparent way to ship it. We kept the dominant cache
 * (per-thread file manager) and accept the cost of {@code initPlugins} per call. The
 * planned next step is {@code parseUnits(List)} (one task per batch), which recovers
 * most of the lost throughput while staying inside the public API.
 *
 * <p>{@link #HAS_JAVAC_INTERNAL_ACCESS} probes {@code jdk.compiler}'s exports at class
 * init; a future opt-in fast path can branch on it before attempting an internal-API
 * shortcut.
 */
public final class JavaParser {

    public static final boolean HAS_JAVAC_INTERNAL_ACCESS = computeHasJavacInternalAccess();

    private static final JavaCompiler COMPILER = lookupCompiler();
    private static final ThreadLocal<StandardJavaFileManager> FILE_MANAGER =
            ThreadLocal.withInitial(() -> COMPILER.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8));
    private static final List<String> TASK_OPTIONS = List.of("-proc:none");

    private static boolean computeHasJavacInternalAccess() {
        final var jdkCompiler = ModuleLayer.boot().findModule("jdk.compiler").orElse(null);
        if (jdkCompiler == null) {
            return false;
        }
        final var self = JavaParser.class.getModule();
        return Stream.of(
                "com.sun.tools.javac.api",
                "com.sun.tools.javac.file",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util")
            .allMatch(p -> jdkCompiler.isExported(p, self));
    }

    public static CompilationUnitTree parse(final String source) {
        return parseUnit(source).tree();
    }

    public static ParsedUnit parseUnit(final String source) {
        Objects.requireNonNull(source, "source");
        final var outcome = parseUnits(List.of(source)).get(0);
        return switch (outcome) {
            case ParseOutcome.Ok ok -> ok.unit();
            case ParseOutcome.Failed failed -> throw failed.error();
        };
    }

    /**
     * Parses {@code sources} as a single {@link JavacTask}, amortizing
     * {@code BasicJavacTask.initPlugins} (a per-task {@link java.util.ServiceLoader} walk)
     * across the whole batch. Order-preserving: result {@code i} corresponds to
     * {@code sources.get(i)}. Per-source parse errors surface as {@link ParseOutcome.Failed};
     * the batch never short-circuits. Errors with no associated source (rare; typically
     * options or environment problems) are attributed to source {@code 0} so they aren't
     * silently dropped.
     */
    public static List<ParseOutcome> parseUnits(final List<String> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            return List.of();
        }

        final var stripped = sources.stream().map(JavaParser::stripBom).toList();
        final var fileObjects = IntStream.range(0, stripped.size())
            .<JavaFileObject>mapToObj(i -> new InMemoryJavaFileObject(stripped.get(i), i))
            .toList();

        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var fileManager = FILE_MANAGER.get();
        final var task = (JavacTask) COMPILER.getTask(null, fileManager, diagnostics, TASK_OPTIONS, null, fileObjects);

        final Iterable<? extends CompilationUnitTree> trees;
        try {
            trees = task.parse();
        } catch (final IOException e) {
            throw new AssertionError(e);
        }

        final var treeByUri = new LinkedHashMap<URI, CompilationUnitTree>();
        for (final var tree : trees) {
            treeByUri.put(tree.getSourceFile().toUri(), tree);
        }

        final var firstUri = fileObjects.get(0).toUri();
        final Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri =
            diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .collect(Collectors.groupingBy(d -> {
                    final var src = d.getSource();
                    return src == null ? firstUri : src.toUri();
                }));

        final var positions = Trees.instance(task).getSourcePositions();
        return IntStream.range(0, stripped.size())
            .<ParseOutcome>mapToObj(i -> outcomeFor(
                i, stripped.get(i), fileObjects.get(i).toUri(), treeByUri, errorsByUri, positions))
            .toList();
    }

    private static ParseOutcome outcomeFor(
            final int index,
            final String source,
            final URI uri,
            final Map<URI, CompilationUnitTree> treeByUri,
            final Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri,
            final SourcePositions positions) {
        final var perSourceErrors = errorsByUri.getOrDefault(uri, List.of());
        if (!perSourceErrors.isEmpty()) {
            final var message = perSourceErrors.stream()
                .map(d -> d.getMessage(Locale.ROOT))
                .collect(Collectors.joining("; "));
            final var asDiagnostics = perSourceErrors.stream()
                .<Diagnostic>map(d -> new Diagnostic.ParseError(d.getMessage(Locale.ROOT), positionOf(d)))
                .toList();
            return new ParseOutcome.Failed(new ParseException(message, asDiagnostics));
        }
        final var tree = treeByUri.get(uri);
        if (tree == null) {
            return new ParseOutcome.Failed(new ParseException(
                "Parser produced no compilation unit for source #" + index, List.of()));
        }
        final var comments = CommentScanner.scan(source);
        return new ParseOutcome.Ok(CommentAttacher.attach(tree, source, positions, comments));
    }

    private static String stripBom(final String source) {
        return !source.isEmpty() && source.charAt(0) == '\uFEFF' ? source.substring(1) : source;
    }

    private static JavaCompiler lookupCompiler() {
        final var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available; run on a JDK, not a JRE");
        }
        return compiler;
    }

    private static Position positionOf(final javax.tools.Diagnostic<? extends JavaFileObject> d) {
        final var offset = d.getStartPosition();
        final var line = d.getLineNumber();
        final var column = d.getColumnNumber();
        if (offset == javax.tools.Diagnostic.NOPOS
                || line == javax.tools.Diagnostic.NOPOS
                || column == javax.tools.Diagnostic.NOPOS) {
            return Position.UNKNOWN;
        }
        return new Position.At((int) line, (int) column, (int) offset);
    }

    static Position positionOf(final SourcePositions positions, final CompilationUnitTree unit, final com.sun.source.tree.Tree node) {
        final var offset = positions.getStartPosition(unit, node);
        if (offset < 0) {
            return Position.UNKNOWN;
        }
        final var lineMap = unit.getLineMap();
        final var line = (int) lineMap.getLineNumber(offset);
        final var column = (int) lineMap.getColumnNumber(offset);
        return new Position.At(line, column, (int) offset);
    }

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {

        private final String source;

        InMemoryJavaFileObject(final String source, final int index) {
            super(URI.create("string:///source-" + index + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private JavaParser() {}
}
