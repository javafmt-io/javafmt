package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import io.javafmt.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
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

    private void enableJavafmt() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
    }

    /**
     * Enables javafmt and points its binary path at a fake {@code javafmt} that understands exactly
     * the fixture inputs these tests format: it reports a supported version, echoes back anything it
     * does not recognize (byte for byte, including a trailing newline if present, so an already
     * formatted file round-trips as a no-op) and returns one non-internal diagnostic on stderr for
     * {@code Broken.java}'s known-unparseable source.
     */
    private void enableJavafmtWithFakeBinary(final Path dir) throws IOException {
        enableJavafmt();
        JavafmtSettings.getInstance(getProject()).setBinaryPath(fakeBinary(dir).toString());
    }

    private static Path fakeBinary(final Path dir) throws IOException {
        // The sentinel ("echo X", stripped back off below) survives $()'s own trailing-newline
        // stripping, so a trailing newline in stdin is preserved in $input exactly as javafmt's
        // own echo-on-failure and idempotent-reformat behaviour require.
        final var script = "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then\n"
                + "    echo 'javafmt 0.2.0'\n"
                + "    exit 0\n"
                + "fi\n"
                + "input=\"$(cat; echo X)\"\n"
                + "input=\"${input%X}\"\n"
                + "case \"$input\" in\n"
                + "    'class Foo { int x; }')\n"
                + "        printf 'class Foo {\\n\\n    int x;\\n}\\n'\n"
                + "        ;;\n"
                + "    'class A { int x; }')\n"
                + "        printf 'class A {\\n\\n    int x;\\n}\\n'\n"
                + "        ;;\n"
                + "    'class B { int x; }')\n"
                + "        printf 'class B {\\n\\n    int x;\\n}\\n'\n"
                + "        ;;\n"
                + "    'class Warned { int x; }')\n"
                + "        printf 'class Warned {\\n\\n    int x;\\n}\\n'\n"
                + "        echo '{\"kind\":\"warning\",\"message\":\"utility class has no private constructor\",\"line\":1,\"col\":1}' >&2\n"
                + "        ;;\n"
                + "    'class Broken { int x = ; }')\n"
                + "        printf '%s' \"$input\"\n"
                + "        echo '{\"kind\":\"error\",\"message\":\"expected an expression\",\"line\":1,\"col\":24}' >&2\n"
                + "        ;;\n"
                + "    *)\n"
                + "        printf '%s' \"$input\"\n"
                + "        ;;\n"
                + "esac\n";
        final var path = dir.resolve("javafmt-fake");
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
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
     * {@code --version}, always reports a supported version, and echoes stdin back unchanged for
     * anything else — so a test can format through it and then assert on how many times its
     * {@code --version} branch actually ran, independent of whatever the format call did.
     */
    private static Path fakeBinaryCountingVersionProbes(final Path dir, final Path counterFile) throws IOException {
        final var script = "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then\n"
                + "    echo x >> '" + counterFile + "'\n"
                + "    echo 'javafmt 0.2.0'\n"
                + "    exit 0\n"
                + "fi\n"
                + "cat\n";
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
