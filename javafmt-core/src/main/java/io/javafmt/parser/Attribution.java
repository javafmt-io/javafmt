package io.javafmt.parser;

import com.sun.source.util.Trees;
import java.util.Objects;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The javac services produced by running attribution ({@link com.sun.source.util.JavacTask#analyze()})
 * on a compilation unit. Holds references to the underlying {@link com.sun.source.util.JavacTask}
 * state, so the task and its file manager must remain reachable for the lifetime of an instance.
 *
 * <p>{@code classpath} is the value that was passed to
 * {@link JavaParser#parseAndAttribute(String, String)}. Rules that need to re-attribute a
 * modified source (e.g. to test whether removing a type witness changes inference) use this
 * to invoke {@code parseAndAttribute} with the same classpath the original attribution saw.
 */
public record Attribution(Trees trees, Types types, Elements elements, String classpath) {

    public Attribution {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(classpath, "classpath");
    }
}
