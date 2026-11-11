package io.javafmt.intellij;

import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Base class for javafmt IntelliJ plugin tests on the light Java fixture. Resets {@link JavafmtSettings} after every test. */
// LightJavaCodeInsightFixtureTestCase's JUnit 3-era runBare() used to dispatch every test onto the
// EDT, which PSI/document APIs require. The platform doesn't do that automatically once tests run
// on the JUnit Platform, so OnTheEdt restores it here.
@ExtendWith(JavafmtIntellijTestCase.OnTheEdt.class)
public abstract class JavafmtIntellijTestCase extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            // The light fixture shares one project across the whole class, so settings written
            // by one test would otherwise leak into the next.
            JavafmtSettings.getInstance(getProject()).loadState(new JavafmtSettings());
            JavafmtApplicationSettings.getInstance().loadState(new JavafmtApplicationSettings());
        } finally {
            super.tearDown();
        }
    }

    protected PsiFile scratchFile(final String name, final String text) {
        final var file = Objects.requireNonNull(ScratchRootType.getInstance().createScratchFile(getProject(), name, JavaLanguage.INSTANCE, text));
        Disposer.register(getTestRootDisposable(), () -> VfsTestUtil.deleteFile(file));
        return psiFile(file);
    }

    protected PsiFile psiFile(final VirtualFile file) {
        return Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(file));
    }

    protected static VirtualFile localFile(final Path path, final String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path));
    }

    // LightJavaCodeInsightFixtureTestCase is JUnit 3-shaped: setUp()/tearDown() are plain
    // overrides, not lifecycle callbacks the JUnit Platform recognizes on its own. These bridge
    // methods invoke them (through Java's normal override dispatch, so subclass setUp()/tearDown()
    // overrides still run) as @BeforeEach/@AfterEach so the fixture is still initialized and torn
    // down around every @Test method.
    @BeforeEach
    final void bridgeSetUp() throws Exception {
        setUp();
    }

    @AfterEach
    final void bridgeTearDown() throws Exception {
        tearDown();
    }

    static final class OnTheEdt implements InvocationInterceptor {

        @Override
        public <T> T interceptTestClassConstructor(final Invocation<T> invocation,
                                                   final ReflectiveInvocationContext<Constructor<T>> context,
                                                   final ExtensionContext extension)
                throws Throwable {
            return onTheEdt(invocation);
        }

        @Override
        public void interceptBeforeEachMethod(final Invocation<Void> invocation,
                                              final ReflectiveInvocationContext<Method> context,
                                              final ExtensionContext extension)
                throws Throwable {
            onTheEdt(invocation);
        }

        @Override
        public void interceptTestMethod(final Invocation<Void> invocation,
                                        final ReflectiveInvocationContext<Method> context,
                                        final ExtensionContext extension)
                throws Throwable {
            onTheEdt(invocation);
        }

        @Override
        public void interceptTestTemplateMethod(final Invocation<Void> invocation,
                                                final ReflectiveInvocationContext<Method> context,
                                                final ExtensionContext extension)
                throws Throwable {
            onTheEdt(invocation);
        }

        @Override
        public void interceptAfterEachMethod(final Invocation<Void> invocation,
                                             final ReflectiveInvocationContext<Method> context,
                                             final ExtensionContext extension)
                throws Throwable {
            onTheEdt(invocation);
        }

        private static <T> T onTheEdt(final Invocation<T> invocation) throws Throwable {
            return EdtTestUtil.runInEdtAndGet(invocation::proceed, true);
        }
    }
}
