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
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * <p><b>Threading.</b> A parse borrows a {@link StandardJavaFileManager} from a shared pool
 * and returns it when the call ends, so javac's boot-classpath scan and {@code JarFile}
 * inflation are paid once per manager rather than once per call. A manager is never used by two
 * calls at once, because it is not thread-safe, and the pool holds no more managers than the most
 * parses that ever ran at the same time, however many threads a long-lived host such as the
 * Gradle daemon cycles through. Attribution has a pool of its own: see {@link #attributing}.
 * Per-call: the {@link com.sun.source.util.JavacTask},
 * its diagnostic collector, and the in-memory {@link JavaFileObject}.
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
 * (a reused file manager) and accept the cost of {@code initPlugins} per task;
 * {@code parseUnits(List)} (one task per batch) recovers most of the lost throughput while
 * staying inside the public API.
 *
 * <p>{@link #HAS_JAVAC_INTERNAL_ACCESS} probes {@code jdk.compiler}'s exports at class
 * init; a future opt-in fast path can branch on it before attempting an internal-API
 * shortcut.
 */
@UtilityClass
public final class JavaParser {

    public final boolean HAS_JAVAC_INTERNAL_ACCESS = computeHasJavacInternalAccess();

    private final JavaCompiler COMPILER = lookupCompiler();

    private final Queue<StandardJavaFileManager> IDLE_FILE_MANAGERS = new ConcurrentLinkedQueue<>();

    final int ATTRIBUTION_POOL_CAPACITY = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * Kept apart from {@link #IDLE_FILE_MANAGERS} because setting {@code -classpath} on a task
     * mutates the manager's classpath location, which would pollute the parse-only path, and keyed
     * by classpath so two classpaths never see each other's locations. Reuse matters because ~96%
     * of a single-file attribution is fixed setup (measured: 6.7 ms for one file, 11.4 ms for 32).
     */
    private final FileManagerPool ATTRIBUTION_FILE_MANAGERS = new FileManagerPool(ATTRIBUTION_POOL_CAPACITY, JavaParser::newFileManager);

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

    private StandardJavaFileManager newFileManager() {
        ensureJavaHome();
        return COMPILER.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
    }

    int idleFileManagers() {
        return IDLE_FILE_MANAGERS.size();
    }

    int idleAttributionFileManagers() {
        return ATTRIBUTION_FILE_MANAGERS.idleCount();
    }

    /**
     * Runs {@code work} with an {@link AttributionSession} on {@code classpath}, lending it a pooled
     * file manager that goes back to the pool when {@code work} returns. Everything that uses the
     * session's {@link Attribution}s, linting included, belongs inside {@code work}: once it
     * returns, another caller may be handed the same manager.
     */
    public <T> T attributing(final String classpath, final Function<AttributionSession, T> work) {
        Objects.requireNonNull(classpath, "classpath");
        Objects.requireNonNull(work, "work");
        final var fileManager = ATTRIBUTION_FILE_MANAGERS.borrow(classpath);
        final var session = new AttributionSession(classpath, fileManager);
        try {
            return work.apply(session);
        } finally {
            session.end();
            ATTRIBUTION_FILE_MANAGERS.giveBack(classpath, fileManager);
        }
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
     * Attributes {@code source} in the same session as {@code like}, for rules that re-attribute a
     * rewritten copy of a file they are already linting. Throws on parse failure, as
     * {@link AttributionSession#attribute(String)} does.
     */
    public AttributedUnit reattribute(final String source, final Attribution like) {
        Objects.requireNonNull(like, "like");
        return like.session().attribute(source);
    }

    List<AttributedOutcome> attributeBatch(final List<String> sources, final AttributionSession session) {
        final var classpath = session.classpath();
        final var options = classpath.isEmpty() ? TASK_OPTIONS : Stream.concat(TASK_OPTIONS.stream(), Stream.of("-classpath", classpath)).toList();
        final var batch = parseBatch(sources, session.fileManager(), options);
        final var task = batch.task();
        try {
            task.analyze();
        } catch (IOException e) {
            throw new AssertionError(e);
        } catch (RuntimeException e) {
            return attributeIndividually(sources, session, e);
        }
        final var attribution = new Attribution(Trees.instance(task), task.getTypes(), task.getElements(), session);
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
    private List<AttributedOutcome> attributeIndividually(
            final List<String> sources,
            final AttributionSession session,
            final RuntimeException batchFailure) {
        if (sources.size() == 1) {
            return List.of(new AttributedOutcome.Failed(batchFailure));
        }
        return sources.stream()
                .map(source -> attributeBatch(List.of(source), session).getFirst())
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
        final var fileManager = Optional.ofNullable(IDLE_FILE_MANAGERS.poll()).orElseGet(JavaParser::newFileManager);
        try {
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
        } finally {
            IDLE_FILE_MANAGERS.offer(fileManager);
        }
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
            return new Position.Unknown();
        }
        return new Position.At((int) line, (int) column, (int) offset);
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
