package io.javafmt.intellij;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Makes IntelliJ indent and wrap while typing the way javafmt will lay the file out, in projects that use javafmt. */
public final class JavafmtCodeStyleModifier implements CodeStyleSettingsModifier {

    private static final int INDENT = 4;

    private static final int CONTINUATION_INDENT = 8;

    private static final int RIGHT_MARGIN = 150;

    @Override
    public boolean modifySettings(@NotNull final TransientCodeStyleSettings settings, @NotNull final PsiFile file) {
        if (!file.getLanguage().isKindOf(JavaLanguage.INSTANCE)
            || !JavafmtSettings.getInstance(file.getProject()).isEnabled()) {
            return false;
        }
        final var java = settings.getCommonSettings(JavaLanguage.INSTANCE);
        java.RIGHT_MARGIN = RIGHT_MARGIN;
        java.WRAP_ON_TYPING = CommonCodeStyleSettings.WrapOnTyping.NO_WRAP.intValue;
        final var indent = java.getIndentOptions();
        if (indent != null) {
            indent.INDENT_SIZE = INDENT;
            indent.TAB_SIZE = INDENT;
            indent.CONTINUATION_INDENT_SIZE = CONTINUATION_INDENT;
            indent.USE_TAB_CHARACTER = false;
        }
        return true;
    }

    @Override
    public boolean mayOverrideSettingsOf(@NotNull final Project project) {
        return JavafmtSettings.getInstance(project).isEnabled();
    }

    @Override
    @NotNull
    public String getName() {
        return "javafmt";
    }

    @Override
    @Nullable
    public CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull final TransientCodeStyleSettings settings) {
        return null;
    }
}
