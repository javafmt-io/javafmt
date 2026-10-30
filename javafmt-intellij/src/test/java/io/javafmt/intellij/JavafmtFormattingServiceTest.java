package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import io.javafmt.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

// Formatting happens by spawning a fake javafmt binary (a /bin/sh script, following the pattern
// established by JavafmtBinaryTest), so this class shares that test's OS restriction.
//
// Every test drives the platform's own entry point, CodeStyleManager.reformatText, rather than
// calling the service directly: which service the platform picks for a given request, and what it
// does with a service that declines, is exactly the part that decides whether Reformat Code works.
@EnabledOnOs({OS.MAC, OS.LINUX})
public class JavafmtFormattingServiceTest extends JavafmtIntellijTestCase {

    /** Source the fake binary answers with a formatted body plus one warning-only diagnostic. */
    private static final String UNFORMATTED_WARNED = "class Warned { int x; }";

    private static final String FORMATTED_WARNED = "class Warned {\n\n    int x;\n}\n";

    private static final String DOWNLOADED = "javafmt-" + FakeRelease.VERSION + "-linux-x64";

    private final List<Notification> notifications = new ArrayList<>();

    private JavafmtFormattingService service;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        service = new JavafmtFormattingService();
        getProject()
                .getMessageBus()
                .connect(getTestRootDisposable())
                .subscribe(
                        Notifications.TOPIC,
                        new Notifications() {

                            @Override
                            public void notify(@NotNull final Notification notification) {
                                notifications.add(notification);
                            }
                        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JavafmtDaemons.getInstance(getProject()).stop();
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void testNotActiveWhenDisabled() {
        final var file = myFixture.configureByText("Foo.java", "class Foo {}");
        assertThat(service.canFormat(file)).isFalse();
    }

    @Test
    public void testActiveForJavaFileWhenEnabled() {
        enableJavafmt();
        final var file = myFixture.configureByText("Foo.java", "class Foo {}");
        assertThat(service.canFormat(file)).isTrue();
    }

    @Test
    public void testNotActiveForNonJavaFile() {
        enableJavafmt();
        final var file = myFixture.configureByText("notes.txt", "hello");
        assertThat(service.canFormat(file)).isFalse();
    }

    @Test
    public void testReformatIsHandledByAnOffEdtFormattingService() {
        enableJavafmt();
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        // A synchronous ExternalFormatProcessor spawns the binary and waits for it on the EDT with
        // the write lock held. AsyncDocumentFormattingService is the platform's supported base for
        // an out-of-process formatter: it runs the task on a pooled thread, under a cancellable
        // progress indicator, and applies the result in a write action of its own.
        assertThat(FormattingServiceUtil.findService(file, true, true)).isInstanceOf(AsyncDocumentFormattingService.class);
    }

    @Test
    public void testDisabledProjectNeverRunsJavafmt(@TempDir final Path dir) throws IOException {
        final var counter = dir.resolve("invocations");
        JavafmtSettings.getInstance(getProject()).setBinaryPath(fakeBinaryCountingVersionProbes(dir, counter).toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(counter).doesNotExist();
    }

    @Test
    public void testFormatsWholeFile(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    public void testUnchangedFileIsLeftAsItIs(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        // Format once to get the canonical form, then verify a second format is a no-op.
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        final var canonical = file.getText();
        reformat(file);
        assertThat(file.getText()).isEqualTo(canonical);
    }

    @Test
    public void testUnparseableSourceIsLeftUntouched(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Broken.java", "class Broken { int x = ; }");
        reformat(file);
        assertThat(file.getText()).isEqualTo("class Broken { int x = ; }");
    }

    @Test
    public void testUnparseableSourceIsReported(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Broken.java", "class Broken { int x = ; }");
        reformat(file);
        // A formatter failure must never look like success; the user gets told javafmt did nothing.
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.getFirst().getContent()).contains("Broken.java");
        assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.WARNING);
    }

    @Test
    public void testAnInternalErrorIsReportedAsAnError(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Internal.java", "class Internal {}");
        reformat(file);
        assertThat(file.getText()).isEqualTo("class Internal {}");
        assertThat(notifications).singleElement().satisfies(notification -> assertThat(notification.getType()).isEqualTo(NotificationType.ERROR));
    }

    @Test
    public void testSuccessfulFormatIsSilent(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(notifications).isEmpty();
    }

    @Test
    public void testWarningOnlyDiagnosticsStillInstallTheFormattedOutput(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Warned.java", UNFORMATTED_WARNED);
        // A warning is advisory: the CLI exits 0 and still prints the formatted source, so the
        // reformat must land rather than being thrown away.
        reformat(file);
        assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo(FORMATTED_WARNED);
    }

    @Test
    public void testWarningOnlyDiagnosticsAreNotReportedAsAFailure(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Warned.java", UNFORMATTED_WARNED);
        reformat(file);
        assertThat(notifications).isEmpty();
    }

    @Test
    public void testNoBinaryFoundWarnsAndLeavesFileUnformatted(@TempDir final Path dir) {
        enableJavafmt();
        // A path that resolves to nothing, regardless of what happens to be on this machine's PATH.
        JavafmtSettings.getInstance(getProject()).setBinaryPath(dir.resolve("nope").toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(file.getText()).isEqualTo(Fixtures.UNFORMATTED_FOO);
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notifications.getFirst().getContent()).contains("No javafmt binary found");
    }

    @Test
    public void testTooOldBinaryWarnsAndLeavesFileUnformatted(@TempDir final Path dir) throws IOException {
        enableJavafmt();
        JavafmtSettings.getInstance(getProject()).setBinaryPath(fakeBinaryReportingVersion(dir, "javafmt 0.0.9").toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(file.getText()).isEqualTo(Fixtures.UNFORMATTED_FOO);
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notifications.getFirst().getContent()).contains(JavafmtBinary.minimumVersion());
    }

    @Test
    public void testReplacedBinaryAtSamePathIsReprobedRatherThanTrustingAStaleCache(
            @TempDir final Path dir)
            throws IOException, InterruptedException {
        enableJavafmt();
        final var path = fakeBinaryReportingVersion(dir, "javafmt 0.2.0");
        JavafmtSettings.getInstance(getProject()).setBinaryPath(path.toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        // The 0.2.0 binary passed the gate; it refuses (exit 1) on anything but --version, so this
        // is the internal-refusal ERROR from an actual format attempt, not the "too old" WARNING.
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.getLast().getType()).isEqualTo(NotificationType.ERROR);
        notifications.clear();
        // Sleep past a whole-second mtime granularity (some filesystems only track that coarsely)
        // so the replacement's mtime is observably later, then overwrite the same path with a
        // binary below the gate. A cache keyed on path alone would still answer "supported" here;
        // keying on path + size + mtime must not.
        Thread.sleep(1_100);
        fakeBinaryReportingVersion(dir, "javafmt 0.0.9");
        reformat(file);
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.getLast().getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notifications.getLast().getContent()).contains(JavafmtBinary.minimumVersion());
    }

    @Test
    public void testVersionProbeIsCachedNotRepeatedPerFormat(@TempDir final Path dir) throws IOException {
        enableJavafmt();
        final var counter = dir.resolve("version-probes");
        JavafmtSettings.getInstance(getProject()).setBinaryPath(fakeBinaryCountingVersionProbes(dir, counter).toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        // Two formats against the same (unmodified) binary path must probe --version once, not twice.
        reformat(file);
        reformat(file);
        assertThat(Files.readString(counter).lines()).hasSize(1);
    }

    @Test
    public void testSuccessiveReformatsShareOneJavafmtProcess(@TempDir final Path dir) throws IOException {
        enableJavafmt();
        final var starts = dir.resolve("daemon-starts");
        final var binary = dir.resolve("javafmt-fake-warm");
        Files.writeString(
                binary,
                "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then\n    echo 'javafmt 0.2.0'\n    exit 0\nfi\necho x >> '" + starts + "'\n"
                        + JavafmtDaemonTest.ECHO_REQUESTS);
        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwxr-xr-x"));
        JavafmtSettings.getInstance(getProject()).setBinaryPath(binary.toString());
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        reformat(file);
        reformat(file);
        assertThat(Files.readString(starts).lines()).hasSize(1);
    }

    @Test
    public void testSelectionReformatGoesToIntellijsOwnFormatter(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        final var file = myFixture.configureByText("Foo.java", "class Foo {\nint x;\n}\n");
        // javafmt lays out a whole compilation unit or nothing, and the fake echoes anything it
        // does not recognise back unchanged — so an indented field is IntelliJ's own formatter
        // having handled the selection, and an unchanged file would be the silent no-op that
        // leaving FORMAT_FRAGMENTS out of getFeatures() exists to prevent.
        reformatRange(file, new TextRange("class Foo {\n".length(), "class Foo {\nint x;\n".length()));
        assertThat(file.getText()).isEqualTo("class Foo {\n    int x;\n}\n");
    }

    @Test
    public void testSelectionReformatIsNotOfferedToJavafmt() {
        enableJavafmt();
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        assertThat(FormattingServiceUtil.findService(file, true, false)).isNotInstanceOf(JavafmtFormattingService.class);
    }

    @Test
    public void testAdHocFormattingIsNotOfferedToJavafmt() {
        enableJavafmt();
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        // Reformatting as you type may only move whitespace; javafmt's lint pass rewrites code.
        assertThat(FormattingServiceUtil.findService(file, false, true)).isNotInstanceOf(JavafmtFormattingService.class);
    }

    @Test
    public void testWithoutABinaryAReformatStartsOneDownloadWithoutWaitingForIt(@TempDir final Path dir) throws Exception {
        enableJavafmt();
        final var binary = Files.readAllBytes(fakeBinary(dir));
        final var go = new CountDownLatch(1);
        try (final var release = FakeRelease.servingAfter(go, Map.of(DOWNLOADED, binary))) {
            final var provider = useDownloadsFrom(release, dir.resolve("cache"), FakeRelease.sha256(binary));
            final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
            reformat(file);
            reformat(file);
            assertThat(file.getText()).isEqualTo(Fixtures.UNFORMATTED_FOO);
            assertThat(notifications)
                    .singleElement()
                    .satisfies(notification -> {
                        assertThat(notification.getType()).isEqualTo(NotificationType.INFORMATION);
                        assertThat(notification.getContent()).contains("downloading");
                    });
            go.countDown();
            assertThat(provider.download(null).get(20, TimeUnit.SECONDS)).isInstanceOf(Installation.Installed.class);
            assertThat(release.requests()).isEqualTo(1);
        }
    }

    @Test
    public void testAReformatAfterTheDownloadFormats(@TempDir final Path dir) throws Exception {
        enableJavafmt();
        final var binary = Files.readAllBytes(fakeBinary(dir));
        try (final var release = FakeRelease.serving(Map.of(DOWNLOADED, binary))) {
            final var provider = useDownloadsFrom(release, dir.resolve("cache"), FakeRelease.sha256(binary));
            assertThat(provider.download(null).get(20, TimeUnit.SECONDS)).isInstanceOf(Installation.Installed.class);
            final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
            reformat(file);
            assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo(Fixtures.FORMATTED_FOO);
            assertThat(notifications).isEmpty();
            assertThat(release.requests()).isEqualTo(1);
        }
    }

    @Test
    public void testAFailedDownloadLeavesTheFileAloneAndSaysHowToFixIt(@TempDir final Path dir) throws IOException {
        enableJavafmt();
        try (final var release = FakeRelease.serving(Map.of())) {
            useDownloadsFrom(release, dir.resolve("cache"), FakeRelease.sha256(new byte[0]));
            final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
            reformat(file);
            PlatformTestUtil.waitWithEventsDispatching(
                    "no download failure notice",
                    () -> notifications.stream().anyMatch(n -> n.getType() == NotificationType.WARNING),
                    20);
            assertThat(file.getText()).isEqualTo(Fixtures.UNFORMATTED_FOO);
            assertThat(notifications.stream().filter(n -> n.getType() == NotificationType.WARNING))
                    .singleElement()
                    .satisfies(notification -> assertThat(notification.getContent()).contains("javafmt.binaryPath"));
        }
    }

    @Test
    public void testAConfiguredBinaryPathNeverDownloads(@TempDir final Path dir) throws IOException {
        enableJavafmtWithFakeBinary(dir);
        try (final var release = FakeRelease.serving(Map.of(DOWNLOADED, new byte[0]))) {
            useDownloadsFrom(release, dir.resolve("cache"), FakeRelease.sha256(new byte[0]));
            final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
            reformat(file);
            assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo(Fixtures.FORMATTED_FOO);
            assertThat(release.requests()).isZero();
        }
    }

    private JavafmtBinaryProvider useDownloadsFrom(final FakeRelease release, final Path cache, final String sha256) {
        final var installer = new BinaryInstaller(release.base(), cache, new ReleaseAsset.Published(DOWNLOADED, sha256));
        final var provider = new JavafmtBinaryProvider(
                installer,
                JavafmtBinaryProviderTest::runOnNewThread,
                () -> cache.resolve("empty-path").toString());
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JavafmtBinaryProvider.class, provider, getTestRootDisposable());
        return provider;
    }

    private void enableJavafmt() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
    }

    /**
     * Enables javafmt and points its binary path at a fake {@code javafmt} that understands exactly
     * the fixture inputs these tests format: it reports a supported version, serves
     * {@code --daemon} requests, echoes back anything it does not recognize (byte for byte, so an
     * already formatted file round-trips as a no-op) and replies with one error diagnostic for
     * {@code Broken.java}'s known-unparseable source.
     */
    private void enableJavafmtWithFakeBinary(final Path dir) throws IOException {
        enableJavafmt();
        JavafmtSettings.getInstance(getProject()).setBinaryPath(fakeBinary(dir).toString());
    }

    private static Path fakeBinary(final Path dir) throws IOException {
        return FakeDaemon.script(dir, "javafmt-fake");
    }

    /** A fake binary that answers {@code --version} with {@code version} and otherwise refuses; used only to probe the version gate. */
    private static Path fakeBinaryReportingVersion(final Path dir, final String version) throws IOException {
        final var script = "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then\n"
                + "    echo '" + version + "'\n"
                + "    exit 0\n"
                + "fi\n"
                + "exit 1\n";
        final var path = dir.resolve("javafmt-fake-old");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }

    /**
     * A fake binary that appends one line to {@code counterFile} every time it is invoked with
     * {@code --version}, always reports a supported version, and otherwise serves {@code --daemon}
     * requests by echoing each source back unchanged — so a test can format through it and then assert on how many times its
     * {@code --version} branch actually ran, independent of whatever the format call did.
     */
    private static Path fakeBinaryCountingVersionProbes(final Path dir, final Path counterFile) throws IOException {
        final var script = "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then\n"
                + "    echo x >> '" + counterFile + "'\n"
                + "    echo 'javafmt 0.2.0'\n"
                + "    exit 0\n"
                + "fi\n" + JavafmtDaemonTest.ECHO_REQUESTS;
        final var path = dir.resolve("javafmt-fake-counting");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }

    /** Reformats the whole file the way <em>Reformat Code</em> does, through the platform's own entry point. */
    private void reformat(final PsiFile file) {
        reformatRange(file, file.getTextRange());
    }

    /** Reformats one range the way <em>Reformat Code</em> on a selection does. */
    private void reformatRange(final PsiFile file, final TextRange range) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject()).reformatText(file, List.of(range)));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
}
