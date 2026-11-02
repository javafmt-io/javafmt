package io.javafmt.intellij;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import io.javafmt.Fixtures;
import io.javafmt.JavafmtConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InProcessFormattingServiceTest extends JavafmtIntellijTestCase {
    private static final String STAR_IMPORT = "import java.util.*;\n\nclass Order { List<String> lines; }\n";

    private final List<Notification> notifications = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
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
    public void testFormatsWholeFileWithoutAnyBinary() {
        final var file = myFixture.configureByText("Foo.java", Fixtures.UNFORMATTED_FOO);
        reformat(file);
        assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo(Fixtures.FORMATTED_FOO);
        assertThat(notifications).isEmpty();
    }

    @Test
    public void testUnparseableSourceIsLeftUntouchedAndReported() {
        final var file = myFixture.configureByText("Broken.java", "class Broken { int x = ; }");
        reformat(file);
        assertThat(file.getText()).isEqualTo("class Broken { int x = ; }");
        assertThat(notifications)
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
                    assertThat(notification.getContent()).contains("Broken.java");
                });
    }

    @Test
    public void testAFileInAModuleIsFormattedWithTheClasspath() {
        final var file = myFixture.configureByText("Order.java", STAR_IMPORT);
        reformat(file);
        assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo("import java.util.List;\n\nclass Order {\n    List<String> lines;\n}\n");
    }

    @Test
    public void testAScratchFileIsFormattedWithoutTheClasspath() {
        final var file = scratchFile("Order.java", STAR_IMPORT);
        reformat(file);
        assertThat(file.getText()).isEqualTo("import java.util.*;\n\nclass Order {\n    List<String> lines;\n}\n");
        assertThat(notifications).isEmpty();
    }

    @Test
    public void testAnInvalidProjectFileLeavesTheFileAloneAndSaysWhy(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=FinalParameters\n");
        final var file = psiFile(localFile(dir.resolve("src/Order.java"), STAR_IMPORT));
        reformat(file);
        assertThat(file.getText()).isEqualTo(STAR_IMPORT);
        assertThat(notifications)
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
                    assertThat(notification.getContent()).contains("Order.java", dir.resolve(".javafmt").toString(), "FinalParameters");
                });
    }

    @Test
    public void testTimingNamesTheFileTheDurationTheEngineAndWhetherItWasAnalyzed() {
        assertThat(
                JavafmtFormattingService.timing(
                        "Foo.java", JavafmtApplicationSettings.Engine.IN_PROCESS, new JavafmtConfig.Analysis.Attributed(""), Duration.ofMillis(12)))
                .isEqualTo("javafmt: formatted Foo.java in 12 ms (in-process, analyzed)");
        assertThat(
                JavafmtFormattingService.timing(
                        "Foo.java", JavafmtApplicationSettings.Engine.NATIVE, new JavafmtConfig.Analysis.ParseOnly(), Duration.ofMillis(3)))
                .isEqualTo("javafmt: formatted Foo.java in 3 ms (native, parse-only)");
    }

    private void reformat(final PsiFile file) {
        WriteCommandAction.runWriteCommandAction(
                getProject(), () -> CodeStyleManager.getInstance(getProject()).reformatText(file, List.of(file.getTextRange())));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
}
