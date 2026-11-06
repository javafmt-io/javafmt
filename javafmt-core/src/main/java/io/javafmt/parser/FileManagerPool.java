package io.javafmt.parser;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.tools.StandardJavaFileManager;

/**
 * Idle attribution file managers, keyed by the classpath their task was given. A manager is lent to
 * one caller at a time and comes back when that caller's attribution work is finished. Holds at most
 * {@code capacity} idle managers across all classpaths; returning one past that closes the one idle
 * the longest, so a long-lived host that sees many classpaths keeps neither an unbounded number of
 * managers nor their jar files open.
 */
final class FileManagerPool {

    private static final System.Logger LOG = System.getLogger(FileManagerPool.class.getName());

    private final int capacity;

    private final Supplier<StandardJavaFileManager> factory;

    private final Deque<Idle> idle = new ArrayDeque<>();

    FileManagerPool(final int capacity, final Supplier<StandardJavaFileManager> factory) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, but was: " + capacity);
        }
        this.capacity = capacity;
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    StandardJavaFileManager borrow(final String classpath) {
        return take(classpath).orElseGet(factory);
    }

    void giveBack(final String classpath, final StandardJavaFileManager manager) {
        evictAfterAdding(new Idle(classpath, manager)).ifPresent(FileManagerPool::close);
    }

    synchronized int idleCount() {
        return idle.size();
    }

    private synchronized Optional<StandardJavaFileManager> take(final String classpath) {
        final var entries = idle.iterator();
        while (entries.hasNext()) {
            final var entry = entries.next();
            if (entry.classpath().equals(classpath)) {
                entries.remove();
                return Optional.of(entry.manager());
            }
        }
        return Optional.empty();
    }

    private synchronized Optional<StandardJavaFileManager> evictAfterAdding(final Idle returned) {
        idle.addFirst(returned);
        return idle.size() > capacity ? Optional.of(idle.removeLast().manager()) : Optional.empty();
    }

    private static void close(final StandardJavaFileManager manager) {
        try {
            manager.close();
        } catch (final IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Could not close an evicted javac file manager", e);
        }
    }

    private record Idle(String classpath, StandardJavaFileManager manager) {}
}
