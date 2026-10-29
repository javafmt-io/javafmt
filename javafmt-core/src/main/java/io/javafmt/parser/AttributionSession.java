package io.javafmt.parser;

import java.util.List;
import java.util.Objects;
import javax.tools.StandardJavaFileManager;

/**
 * One classpath and the javac file manager attribution runs on, for the duration of a
 * {@link JavaParser#attributing} call. Every unit attributed through a session shares its manager,
 * which is not thread-safe, so a session and the {@link Attribution}s it produces stay on the thread
 * that opened it. Once the {@code attributing} call returns, the manager may be lent to another
 * caller or closed, so the session refuses to attribute anything further. Its {@code Trees} and
 * {@code Elements} cannot be guarded the same way and must not be used past that point either.
 */
public final class AttributionSession {

    private final String classpath;

    private final StandardJavaFileManager fileManager;

    private volatile boolean ended;

    AttributionSession(final String classpath, final StandardJavaFileManager fileManager) {
        this.classpath = Objects.requireNonNull(classpath, "classpath");
        this.fileManager = Objects.requireNonNull(fileManager, "fileManager");
    }

    public String classpath() {
        return classpath;
    }

    StandardJavaFileManager fileManager() {
        return fileManager;
    }

    void end() {
        ended = true;
    }

    /** Whether the {@link JavaParser#attributing} call that opened this session has returned. */
    public boolean hasEnded() {
        return ended;
    }

    /**
     * Parses {@code sources} as a single {@link com.sun.source.util.JavacTask} and runs
     * attribution, amortizing task setup across the batch the way {@link JavaParser#parseUnits}
     * does for parse-only. All returned {@link AttributedUnit}s share the same {@link Attribution}
     * services, which also means cross-file type references resolve within the batch.
     *
     * <p>Per-source parse errors surface as a {@link Staged.Failed} carrying the
     * {@link ParseException}; the batch never short-circuits. An attribution failure (from
     * {@code task.analyze()}) does not either: the batch is re-attributed one source at a time so
     * that only the source javac choked on comes back {@link Staged.Failed}, carrying javac's
     * exception. Sources recovered that way each carry their own {@link Attribution}, so cross-file
     * references no longer resolve within a degraded batch.
     */
    public List<Staged<AttributedUnit>> attribute(final List<String> sources) {
        Objects.requireNonNull(sources, "sources");
        if (ended) {
            throw new IllegalStateException("This attribution session has ended; attribute inside JavaParser.attributing");
        }
        return sources.isEmpty() ? List.of() : JavaParser.attributeBatch(sources, this);
    }

    /**
     * Attributes one source, returning the parsed unit together with javac's {@code Trees},
     * {@code Types} and {@code Elements} services. Throws on parse failure, as
     * {@link JavaParser#parseUnit(String)} does.
     */
    public AttributedUnit attribute(final String source) {
        Objects.requireNonNull(source, "source");
        return attribute(List.of(source)).getFirst().orElseThrow();
    }
}
