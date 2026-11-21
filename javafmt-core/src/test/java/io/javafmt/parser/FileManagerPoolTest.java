package io.javafmt.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.StandardJavaFileManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileManagerPoolTest {

    private final Set<StandardJavaFileManager> closed = Collections.newSetFromMap(new IdentityHashMap<>());

    @Test
    void returningPastCapacityClosesTheManagerIdleTheLongest() {
        final var pool = new FileManagerPool(2, this::recordingManager);
        final var oldest = pool.borrow("a");
        final var middle = pool.borrow("b");
        final var newest = pool.borrow("c");
        pool.giveBack("a", oldest);
        pool.giveBack("b", middle);
        pool.giveBack("c", newest);
        assertThat(closed).singleElement().isSameAs(oldest);
        assertThat(pool.idleCount()).isEqualTo(2);
        assertThat(pool.borrow("b")).isSameAs(middle);
        assertThat(pool.borrow("c")).isSameAs(newest);
    }

    @Test
    void aBorrowedManagerIsNeverLentTwice() {
        final var pool = new FileManagerPool(2, this::recordingManager);
        final var lent = pool.borrow("a");
        assertThat(pool.borrow("a")).isNotSameAs(lent);
    }

    static Stream<Runnable> failures() {
        return Stream.of(
                () -> {
                    throw new IllegalStateException("work failed");
                },
                () -> {
                    throw new OutOfMemoryError("work failed");
                });
    }

    @ParameterizedTest
    @MethodSource("failures")
    void aManagerWhoseWorkThrowsIsClosedInsteadOfPooled(final Runnable failure) {
        final var pool = new FileManagerPool(2, this::recordingManager);
        final var lent = new AtomicReference<StandardJavaFileManager>();
        assertThatThrownBy(() -> pool.lend("a", manager -> {
            lent.set(manager);
            failure.run();
            return manager;
        }))
                .hasMessage("work failed");
        assertThat(closed).singleElement().isSameAs(lent.get());
        assertThat(pool.idleCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("failures")
    void aManagerThatAlsoFailsToCloseKeepsTheWorksFailure(final Runnable failure) {
        final var closing = new IllegalStateException("close failed");
        final var pool = new FileManagerPool(2, () -> managerWhoseCloseThrows(closing));
        assertThatThrownBy(() -> pool.lend("a", manager -> {
            failure.run();
            return manager;
        }))
                .hasMessage("work failed")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(closing));
        assertThat(pool.idleCount()).isZero();
    }

    @Test
    void aManagerWhoseCloseRethrowsTheWorksOwnFailureStillThrowsIt() {
        final var shared = new IllegalStateException("work failed");
        final var pool = new FileManagerPool(2, () -> managerWhoseCloseThrows(shared));
        assertThatThrownBy(() -> pool.lend("a", _ -> {
            throw shared;
        }))
                .isSameAs(shared)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());
    }

    @Test
    void aManagerWhoseWorkReturnsGoesBackToThePool() {
        final var pool = new FileManagerPool(2, this::recordingManager);
        final var lent = pool.lend("a", Function.identity());
        assertThat(closed).isEmpty();
        assertThat(pool.borrow("a")).isSameAs(lent);
    }

    @Test
    void aCapacityBelowOneIsRejected() {
        assertThatThrownBy(() -> new FileManagerPool(0, this::recordingManager)).isInstanceOf(IllegalArgumentException.class);
    }

    private StandardJavaFileManager recordingManager() {
        final var manager = new StandardJavaFileManager[1];
        manager[0] = (StandardJavaFileManager) Proxy.newProxyInstance(
                StandardJavaFileManager.class.getClassLoader(),
                new Class<?>[]{StandardJavaFileManager.class},
                (_, method, _) -> {
                    if (method.getName().equals("close")) {
                        closed.add(manager[0]);
                    }
                    return null;
                });
        return manager[0];
    }

    private static StandardJavaFileManager managerWhoseCloseThrows(final RuntimeException closing) {
        return (StandardJavaFileManager) Proxy.newProxyInstance(
                StandardJavaFileManager.class.getClassLoader(),
                new Class<?>[]{StandardJavaFileManager.class},
                (_, method, _) -> {
                    if (method.getName().equals("close")) {
                        throw closing;
                    }
                    return null;
                });
    }
}
