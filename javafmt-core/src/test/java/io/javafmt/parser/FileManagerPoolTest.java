package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.tools.StandardJavaFileManager;
import org.junit.jupiter.api.Test;

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

    @Test
    void aCapacityBelowOneIsRejected() {
        assertThatThrownBy(() -> new FileManagerPool(0, this::recordingManager)).isInstanceOf(IllegalArgumentException.class);
    }

    private StandardJavaFileManager recordingManager() {
        final var manager = new StandardJavaFileManager[1];
        manager[0] = (StandardJavaFileManager) Proxy.newProxyInstance(
                StandardJavaFileManager.class.getClassLoader(),
                new Class<?>[] {StandardJavaFileManager.class,},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        closed.add(manager[0]);
                    }
                    return null;
                });
        return manager[0];
    }
}
