package io.javafmt;

import com.sun.source.tree.ClassTree;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Compiles a formatted source and reports the errors that indicate javafmt broke it.
 *
 * <p>Re-parsing only proves the output is syntactically valid; it says nothing about the lint
 * auto-fixes that produce well-formed but non-compiling code — a {@code final} field whose
 * initializer was stripped, a {@code var} that widened a diamond's inferred type, a lambda
 * initializer that can no longer be inferred. Those are attribution and flow errors, so only
 * a real compile finds them.
 *
 * <p><b>Sources that can't be judged report {@link Outcome.Skipped}.</b> Most fixtures are
 * snippets that reference types which aren't on the classpath, and javac cascades a wall of
 * follow-on errors from each unresolved name. When any symbol fails to resolve, or the source
 * doesn't parse, or it's a module declaration, the result is {@code Skipped} rather than a
 * guess at which of the remaining errors are real. Self-contained sources — which the oracle
 * corpus files are — get the full check. {@code Skipped} is deliberately distinct from
 * {@link Outcome.Compiles} so a suite can assert it isn't skipping everything.
 */
public final class CompileCheck {

    private static final List<String> UNRESOLVABLE_CODE_PREFIXES = List.of(
            "compiler.err.cant.resolve",
            "compiler.err.doesnt.exist",
            "compiler.err.cant.access",
            "compiler.err.package.not.visible",
            "compiler.err.not.def.access");

    public static Outcome check(final String source) {
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) {
            return new Outcome.Skipped("source is blank");
        }
        final var name = topLevelName(source);
        if (name == null) {
            return new Outcome.Skipped("source does not parse, or is a module declaration");
        }
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var collector = new DiagnosticCollector<JavaFileObject>();
        try (
                final var standard = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
                final var fileManager = new DiscardingFileManager(standard)) {
            final var task = compiler.getTask(
                    null,
                    fileManager,
                    collector,
                    List.of("-proc:none", "-nowarn"),
                    null,
                    List.of(new InMemorySource(name, source)));
            task.call();
        } catch (IOException e) {
            throw new IllegalStateException("compile check failed to close its file manager", e);
        }
        final var errors = collector.getDiagnostics()
                .stream()
                .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .toList();
        if (errors.stream().anyMatch(d -> isUnresolvable(d.getCode()))) {
            return new Outcome.Skipped("references symbols that aren't on the classpath");
        }
        if (errors.isEmpty()) {
            return new Outcome.Compiles();
        }
        return new Outcome.Broken(errors.stream().map(d -> d.getLineNumber() + ": " + d.getMessage(Locale.ROOT)).toList());
    }

    private static boolean isUnresolvable(final String code) {
        return UNRESOLVABLE_CODE_PREFIXES.stream().anyMatch(code::startsWith);
    }

    /**
     * The file name has to match a {@code public} top-level type, so it is derived from the
     * source rather than fixed. Returns {@code null} for sources this check skips: anything
     * that doesn't parse, and module declarations (which can't compile standalone).
     */
    @org.jspecify.annotations.Nullable private static String topLevelName(final String source) {
        final com.sun.source.tree.CompilationUnitTree tree;
        try {
            tree = io.javafmt.parser.JavaParser.parse(source);
        } catch (RuntimeException e) {
            return null;
        }
        if (tree.getModule() != null) {
            return null;
        }
        return tree.getTypeDecls()
                .stream()
                .flatMap(t -> t instanceof ClassTree c ? java.util.stream.Stream.of(c) : java.util.stream.Stream.empty())
                .map(c -> c.getSimpleName().toString())
                .filter(n -> !n.isEmpty())
                .findFirst()
                .orElse("package-info");
    }

    /** What a compile check concluded about one source. */
    public sealed interface Outcome {

        record Compiles() implements Outcome {}

        record Broken(List<String> errors) implements Outcome {}

        record Skipped(String reason) implements Outcome {}
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String source;

        InMemorySource(final String name, final String source) {
            super(URI.create("string:///" + name + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /** Keeps generated class files in memory so a compile check never touches the build tree. */
    private static final class DiscardingFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        DiscardingFileManager(final JavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                final Location location,
                final String className,
                final JavaFileObject.Kind kind,
                final FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {

                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }
    }

    private CompileCheck() {}
}
