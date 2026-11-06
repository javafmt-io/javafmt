package io.javafmt.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The javac services produced by running attribution ({@link com.sun.source.util.JavacTask#analyze()})
 * on a compilation unit. Holds references to the underlying {@link com.sun.source.util.JavacTask}
 * state, so an instance is only usable while its {@link AttributionSession} is: inside the
 * {@link JavaParser#attributing} call that produced it, on the thread that made that call.
 *
 * <p>{@code session} is the {@link AttributionSession} the task ran in. Rules that re-attribute a
 * modified source (e.g. to test whether removing a type witness changes inference) pass this to
 * {@link JavaParser#reattribute}, which reuses its classpath and file manager.
 *
 * <p>{@code errors} holds every error javac reported while attributing, keyed by the URI of the
 * source it came from.
 */
public record Attribution(Trees trees,
                          Types types,
                          Elements elements,
                          AttributionSession session,
                          Map<URI, List<AttributionError>> errors,
                          List<String> batch) {

    public Attribution {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(session, "session");
        errors = Map.copyOf(Objects.requireNonNull(errors, "errors"));
        batch = List.copyOf(Objects.requireNonNull(batch, "batch"));
    }

    public List<AttributionError> errorsIn(final CompilationUnitTree unit) {
        return errors.getOrDefault(unit.getSourceFile().toUri(), List.of());
    }

    public String classpath() {
        return session.classpath();
    }
}
