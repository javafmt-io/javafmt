package io.javafmt.intellij;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtCodeStyleModifierTest extends JavafmtIntellijTestCase {

    private static final int FOREIGN_CONTINUATION_INDENT = 2;

    @Test
    public void testEnabledProjectTypesWithJavafmtsIndentation() {
        withTheProjectsOwnCodeStyle(() -> {
            JavafmtSettings.getInstance(getProject()).setEnabled(true);
            final var file = myFixture.configureByText("A.java", "class A {}");
            final var settings = computedSettings(file);
            final var indent = settings.getIndentOptions(JavaFileType.INSTANCE);
            assertThat(indent.INDENT_SIZE).isEqualTo(4);
            assertThat(indent.CONTINUATION_INDENT_SIZE).isEqualTo(8);
            assertThat(indent.TAB_SIZE).isEqualTo(4);
            assertThat(indent.USE_TAB_CHARACTER).isFalse();
            assertThat(settings.getRightMargin(JavaLanguage.INSTANCE)).isEqualTo(150);
            assertThat(settings.getCommonSettings(JavaLanguage.INSTANCE).WRAP_ON_TYPING)
                    .isEqualTo(CommonCodeStyleSettings.WrapOnTyping.NO_WRAP.intValue);
        });
    }

    @Test
    public void testEnabledProjectOverridesWhateverTheIdeStyleSays() {
        withTheProjectsOwnCodeStyle(() -> withContinuationIndent(FOREIGN_CONTINUATION_INDENT, () -> {
            JavafmtSettings.getInstance(getProject()).setEnabled(true);
            final var file = myFixture.configureByText("A.java", "class A {}");
            assertThat(computedSettings(file).getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE)
                    .isEqualTo(8);
        }));
    }

    @Test
    public void testDisabledProjectKeepsTheIdeStyle() {
        withTheProjectsOwnCodeStyle(() -> withContinuationIndent(FOREIGN_CONTINUATION_INDENT, () -> {
            final var file = myFixture.configureByText("A.java", "class A {}");
            assertThat(computedSettings(file).getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE)
                    .isEqualTo(FOREIGN_CONTINUATION_INDENT);
        }));
    }

    @Test
    public void testIsRegisteredWithThePlatform() {
        assertThat(CodeStyleSettingsModifier.EP_NAME.getExtensionList()).anyMatch(JavafmtCodeStyleModifier.class::isInstance);
    }

    @Test
    public void testAppliesDirectlyToAJavaFileOfAnEnabledProject() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        final var file = myFixture.configureByText("A.java", "class A {}");
        final var transientSettings = new TransientCodeStyleSettings(file.getVirtualFile(), getProject(), CodeStyle.getSettings(getProject()));
        assertThat(new JavafmtCodeStyleModifier().modifySettings(transientSettings, file))
                .isTrue();
        assertThat(transientSettings.getRightMargin(JavaLanguage.INSTANCE)).isEqualTo(150);
    }

    @Test
    public void testIgnoresFilesThatAreNotJava() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        final var file = myFixture.configureByText(PlainTextFileType.INSTANCE, "notes");
        final var transientSettings = new TransientCodeStyleSettings(file.getVirtualFile(), getProject(), CodeStyle.getSettings(getProject()));
        assertThat(new JavafmtCodeStyleModifier().modifySettings(transientSettings, file))
                .isFalse();
    }

    private void withContinuationIndent(final int size, final Runnable test) {
        final var options = CodeStyle.getSettings(getProject()).getIndentOptions(JavaFileType.INSTANCE);
        final var original = options.CONTINUATION_INDENT_SIZE;
        options.CONTINUATION_INDENT_SIZE = size;
        try {
            test.run();
        } finally {
            options.CONTINUATION_INDENT_SIZE = original;
        }
    }
}
