package io.javafmt.intellij;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Base class for javafmt IntelliJ plugin tests that read or write {@link JavafmtSettings}. */
// LightJavaCodeInsightFixtureTestCase's JUnit 3-era runBare() used to dispatch every test onto the
// EDT, which PSI/document APIs require. The platform doesn't do that automatically once tests run
// on the JUnit Platform, so @RunInEdt (the platform's own JUnit 5 extension) restores it here.
@RunInEdt(writeIntent = true)
public abstract class JavafmtIntellijTestCase extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            // The light fixture shares one project across the whole class, so settings written
            // by one test would otherwise leak into the next.
            JavafmtSettings.getInstance(getProject()).loadState(new JavafmtSettings.State());
        } finally {
            super.tearDown();
        }
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
}
