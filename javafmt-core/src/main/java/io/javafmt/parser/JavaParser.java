package io.javafmt.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import io.javafmt.Diagnostic;
import io.javafmt.Position;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.experimental.UtilityClass;

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
 * the Gradle daemon hosting {@code javafmt-spotless}, Maven's forked process for
 * {@code javafmt-maven-plugin}, and IntelliJ for {@code javafmt-intellij} — would have
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
@UtilityClass
public final class JavaParser {

    public final boolean HAS_JAVAC_INTERNAL_ACCESS = computeHasJavacInternalAccess();

    private final JavaCompiler COMPILER = lookupCompiler();

    private final ThreadLocal<StandardJavaFileManager> FILE_MANAGER = ThreadLocal.withInitial(() -> {
        ensureJavaHome();
        return COMPILER.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
    });

    /**
     * File managers for the attributed path, keyed by classpath. Kept out of {@link #FILE_MANAGER}
     * because setting {@code -classpath} on a task mutates the manager's classpath location, which
     * would pollute the parse-only fast path; keyed by classpath so two different classpaths never
     * see each other's locations. {@link ThreadLocal} for the same reason as the parse-only manager
     * — {@link StandardJavaFileManager} is not thread-safe. Never closed: the {@link Attribution}
     * services handed back to callers keep references into it.
     *
     * <p>Reuse matters because ~96% of a single-file attribution is fixed setup (measured: 6.7 ms
     * for one file, 11.4 ms for 32). {@code RedundantTypeArguments} re-attributes once per file
     * that carries a type witness, so a fresh manager per call roughly doubled {@code --analyze}.
     */
    private final ThreadLocal<Map<String, StandardJavaFileManager>> ATTRIBUTION_FILE_MANAGERS = ThreadLocal.withInitial(LinkedHashMap::new);

    private final List<String> TASK_OPTIONS = List.of("-proc:none");

    private final String NO_COMPILATION_UNIT = "Parser produced no compilation unit for source #";

    /**
     * GraalVM native images don't set {@code java.home}; javac's {@code Locations.<clinit>}
     * requires it to be non-null. Any existing path works — we only parse, never load class files.
     */
    private void ensureJavaHome() {
        if (System.getProperty("java.home") == null) {
            System.setProperty("java.home", System.getProperty("user.dir", "."));
        }
    }

    private StandardJavaFileManager attributionFileManager(final String classpath) {
        return ATTRIBUTION_FILE_MANAGERS.get()
                .computeIfAbsent(classpath, ignored -> COMPILER.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8));
    }

    private boolean computeHasJavacInternalAccess() {
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

    public CompilationUnitTree parse(final String source) {
        return parseUnit(source).tree();
    }

    public ParsedUnit parseUnit(final String source) {
        Objects.requireNonNull(source, "source");
        final var outcome = parseUnits(List.of(source)).getFirst();
        return switch (outcome) {
            case ParseOutcome.Ok ok -> ok.unit();
            case ParseOutcome.Failed failed -> throw failed.error();
        };
    }

    /**
     * Parses {@code source} and runs javac's attribution pass, returning the parsed unit
     * together with javac's {@link Trees}, {@link javax.lang.model.util.Types}, and
     * {@link javax.lang.model.util.Elements} services. Use this for lint rules that need
     * resolved type information.
     *
     * <p>Single-source convenience over {@link #parseAndAttribute(List, String)}. Throws on
     * parse failure (mirrors {@link #parseUnit(String)}'s contract).
     */
    public AttributedUnit parseAndAttribute(final String source, final String classpath) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(classpath, "classpath");
        final var outcome = parseAndAttribute(List.of(source), classpath).getFirst();
        return switch (outcome) {
            case AttributedOutcome.Ok ok -> ok.unit();
            case AttributedOutcome.Failed failed -> throw failed.error();
        };
    }

    /**
     * Parses {@code sources} as a single {@link JavacTask} and runs attribution, amortizing
     * task setup across the batch the way {@link #parseUnits} does for parse-only. All
     * returned {@link AttributedUnit}s share the same {@link Attribution} services, which
     * also means cross-file type references resolve within the batch.
     *
     * <p>The task runs on {@link #ATTRIBUTION_FILE_MANAGERS}' manager for {@code classpath},
     * never {@link #FILE_MANAGER}: setting {@code -classpath} on the task mutates the file
     * manager's classpath location, which would pollute the parse-only fast path. Those
     * managers are intentionally not closed because the returned {@link Attribution}'s
     * services keep references into them.
     *
     * <p>Per-source parse errors surface as {@link AttributedOutcome.Failed}; the batch
     * never short-circuits. An attribution failure (from {@code task.analyze()}) does not
     * either: the batch is re-attributed one source at a time so that only the source javac
     * choked on comes back {@link AttributedOutcome.Failed}. Sources recovered that way each
     * carry their own {@link Attribution}, so cross-file references no longer resolve within
     * a degraded batch.
     */
    public List<AttributedOutcome> parseAndAttribute(final List<String> sources, final String classpath) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(classpath, "classpath");
        if (sources.isEmpty()) {
            return List.of();
        }
        return attributeBatch(sources, classpath);
    }

    private List<AttributedOutcome> attributeBatch(final List<String> sources, final String classpath) {
        final var options = classpath.isEmpty() ? TASK_OPTIONS : Stream.concat(TASK_OPTIONS.stream(), Stream.of("-classpath", classpath)).toList();
        ensureJavaHome();
        final var fileManager = attributionFileManager(classpath);
        final var batch = parseBatch(sources, fileManager, options);
        final var task = batch.task();
        try {
            task.analyze();
        } catch (IOException e) {
            throw new AssertionError(e);
        } catch (RuntimeException e) {
            return attributeIndividually(sources, classpath, e);
        }
        final var attribution = new Attribution(Trees.instance(task), task.getTypes(), task.getElements(), classpath);
        final var positions = attribution.trees().getSourcePositions();
        return IntStream.range(0, batch.stripped().size())
                .<AttributedOutcome>mapToObj(
                        i -> attributedOutcomeFor(
                                i,
                                batch.stripped().get(i),
                                batch.fileObjects().get(i).toUri(),
                                batch.treeByUri(),
                                batch.errorsByUri(),
                                positions,
                                attribution))
                .toList();
    }

    /**
     * Re-attributes a batch whose {@code analyze()} blew up, one source at a time, so that the
     * sources javac can handle keep their attribution. A single source that fails again lands
     * here with nothing left to split, and carries {@code batchFailure} out as its own failure.
     */
    private List<AttributedOutcome> attributeIndividually(final List<String> sources, final String classpath, final RuntimeException batchFailure) {
        if (sources.size() == 1) {
            return List.of(new AttributedOutcome.Failed(batchFailure));
        }
        return sources.stream()
                .map(source -> attributeBatch(List.of(source), classpath).getFirst())
                .toList();
    }

    /**
     * Groups javac's error diagnostics by the source they came from. Errors with no associated
     * source (rare; typically options or environment problems) are attributed to
     * {@code fallbackUri} so they aren't silently dropped.
     */
    private Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri(
            final DiagnosticCollector<JavaFileObject> diagnostics,
            final URI fallbackUri) {
        return diagnostics.getDiagnostics()
                .stream()
                .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .collect(
                        Collectors.groupingBy(d -> {
                            final var src = d.getSource();
                            return src == null ? fallbackUri : src.toUri();
                        }));
    }

    private AttributedOutcome attributedOutcomeFor(
            final int index,
            final String source,
            final URI uri,
            final Map<URI, CompilationUnitTree> treeByUri,
            final Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri,
            final SourcePositions positions,
            final Attribution attribution) {
        return resolve(
                index,
                source,
                uri,
                treeByUri,
                errorsByUri,
                positions,
                unit -> new AttributedOutcome.Ok(new AttributedUnit(unit, attribution)),
                AttributedOutcome.Failed::new);
    }

    /**
     * Resolves source #{@code index} against the maps a parse batch produced: a parse error
     * (from {@code errorsByUri}) or a missing tree short-circuits to {@code onError}, otherwise
     * the tree is comment-attached into a {@link ParsedUnit} and handed to {@code onOk}. Shared
     * by {@link #outcomeFor} and {@link #attributedOutcomeFor}, which differ only in what they
     * wrap the result in.
     */
    private <T> T resolve(
            final int index,
            final String source,
            final URI uri,
            final Map<URI, CompilationUnitTree> treeByUri,
            final Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri,
            final SourcePositions positions,
            final Function<ParsedUnit, T> onOk,
            final Function<ParseException, T> onError) {
        final var perSourceErrors = errorsByUri.getOrDefault(uri, List.of());
        if (!perSourceErrors.isEmpty()) {
            return onError.apply(toParseException(perSourceErrors));
        }
        final var tree = treeByUri.get(uri);
        if (tree == null) {
            return onError.apply(new ParseException(NO_COMPILATION_UNIT + index, List.of()));
        }
        final var comments = CommentScanner.scan(source);
        final var unit = CommentAttacher.attach(tree, source, positions, comments);
        return onOk.apply(unit);
    }

    private ParseException toParseException(final List<javax.tools.Diagnostic<? extends JavaFileObject>> errors) {
        final var message = errors.stream()
                .map(d -> d.getMessage(Locale.ROOT))
                .collect(Collectors.joining("; "));
        final var asDiagnostics = errors.stream()
                .<Diagnostic>map(d -> new Diagnostic.ParseError(d.getMessage(Locale.ROOT), positionOf(d)))
                .toList();
        return new ParseException(message, asDiagnostics);
    }

    /**
     * Parses {@code sources} as a single {@link JavacTask}, amortizing
     * {@code BasicJavacTask.initPlugins} (a per-task {@link java.util.ServiceLoader} walk)
     * across the whole batch. Order-preserving: result {@code i} corresponds to
     * {@code sources.get(i)}. Per-source parse errors surface as {@link ParseOutcome.Failed};
     * the batch never short-circuits.
     */
    public List<ParseOutcome> parseUnits(final List<String> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            return List.of();
        }
        final var fileManager = FILE_MANAGER.get();
        final var batch = parseBatch(sources, fileManager, TASK_OPTIONS);
        final var positions = Trees.instance(batch.task()).getSourcePositions();
        return IntStream.range(0, batch.stripped().size())
                .<ParseOutcome>mapToObj(
                        i -> outcomeFor(
                                i,
                                batch.stripped().get(i),
                                batch.fileObjects().get(i).toUri(),
                                batch.treeByUri(),
                                batch.errorsByUri(),
                                positions))
                .toList();
    }

    /**
     * Parses {@code sources} as a single {@link JavacTask}, stopping short of attribution.
     * Shared spine for the parse-only path ({@link #parseUnits}) and the attributed path
     * ({@link #attributeBatch}), which continues on {@link ParsedBatch#task()} with
     * {@code task.analyze()}.
     */
    private ParsedBatch parseBatch(final List<String> sources, final StandardJavaFileManager fileManager, final List<String> options) {
        final var stripped = sources.stream().map(JavaParser::stripBom).toList();
        final var fileObjects = IntStream.range(0, stripped.size())
                .<JavaFileObject>mapToObj(i -> new InMemoryJavaFileObject(stripped.get(i), i))
                .toList();
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var task = (JavacTask) COMPILER.getTask(null, fileManager, diagnostics, options, null, fileObjects);
        final Iterable<? extends CompilationUnitTree> trees;
        try {
            trees = task.parse();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        final var treeByUri = treeByUri(trees);
        final var errorsByUri = errorsByUri(diagnostics, fileObjects.getFirst().toUri());
        return new ParsedBatch(stripped, fileObjects, treeByUri, errorsByUri, task);
    }

    /**
     * Builds a URI-to-tree index from the trees {@code task.parse()} produced. Called
     * immediately after {@code parse()} and before {@code analyze()} runs: {@link JavacTask}
     * only contracts that {@code parse()}'s iterable is valid until the next phase, so both
     * the parse-only and attributed paths drain it here. The merge function is unreachable in
     * practice — javac assigns each in-memory source a distinct {@code Source<i>.java} URI per
     * batch — but {@code toMap}'s map-supplier overload requires one.
     */
    private Map<URI, CompilationUnitTree> treeByUri(final Iterable<? extends CompilationUnitTree> trees) {
        return StreamSupport.stream(trees.spliterator(), false)
                .map(CompilationUnitTree.class::cast)
                .collect(Collectors.toMap(t -> t.getSourceFile().toUri(), Function.identity(), (a, b) -> b, LinkedHashMap::new));
    }

    private ParseOutcome outcomeFor(
            final int index,
            final String source,
            final URI uri,
            final Map<URI, CompilationUnitTree> treeByUri,
            final Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri,
            final SourcePositions positions) {
        return resolve(index, source, uri, treeByUri, errorsByUri, positions, ParseOutcome.Ok::new, ParseOutcome.Failed::new);
    }

    private String stripBom(final String source) {
        return !source.isEmpty() && source.charAt(0) == '\uFEFF' ? source.substring(1) : source;
    }

    /**
     * The system Java compiler, falling back to direct instantiation in host environments
     * (e.g. IntelliJ + JBR) where a custom system class loader blocks
     * {@link ToolProvider}'s module-layer service lookup. IntelliJ always opens
     * {@code jdk.compiler/com.sun.tools.javac.api} to unnamed modules, so direct
     * instantiation via the platform class loader works there.
     */
    private JavaCompiler lookupCompiler() {
        final var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            return compiler;
        }
        try {
            final var cls = Class.forName("com.sun.tools.javac.api.JavacTool", true, ClassLoader.getPlatformClassLoader());
            return (JavaCompiler) cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No Java compiler available; run on a JDK, not a JRE", e);
        }
    }

    private Position positionOf(final javax.tools.Diagnostic<? extends JavaFileObject> d) {
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

    Position positionOf(final SourcePositions positions, final CompilationUnitTree unit, final com.sun.source.tree.Tree node) {
        final var offset = positions.getStartPosition(unit, node);
        if (offset < 0) {
            return Position.UNKNOWN;
        }
        final var lineMap = unit.getLineMap();
        final var line = (int) lineMap.getLineNumber(offset);
        final var column = (int) lineMap.getColumnNumber(offset);
        return new Position.At(line, column, (int) offset);
    }

    private record ParsedBatch(
            List<String> stripped,
            List<JavaFileObject> fileObjects,
            Map<URI, CompilationUnitTree> treeByUri,
            Map<URI, List<javax.tools.Diagnostic<? extends JavaFileObject>>> errorsByUri,
            JavacTask task) {}

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {

        private final String source;

        /**
         * {@code index} is rendered as {@code Source<index>.java}: compact source files
         * (unnamed classes) require a valid Java identifier as the file stem, hyphens are
         * rejected, and a capital {@code S} plus a decimal index is always a valid name.
         */
        InMemoryJavaFileObject(final String source, final int index) {
            super(URI.create("string:///Source" + index + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
