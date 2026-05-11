package io.javafmt.intellij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import io.javafmt.Javafmt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavafmtExternalFormatProcessor implements ExternalFormatProcessor {

    @Override
    public @NotNull String getId() {
        return "javafmt";
    }

    @Override
    public boolean activeForFile(@NotNull PsiFile source) {
        return source instanceof PsiJavaFile;
    }

    @Override
    public @Nullable TextRange format(
            @NotNull PsiFile source,
            @NotNull TextRange range,
            boolean canChangeWhiteSpaceOnly,
            boolean keepLineBreaks,
            boolean enableBulkUpdate,
            int cursorOffset) {
        // Only handle whole-file formatting; return null for selections so IntelliJ's
        // own formatter handles them (javafmt always formats the full compilation unit).
        if (range.getStartOffset() != 0 || range.getEndOffset() != source.getTextLength()) {
            return null;
        }
        final String content = source.getText();
        final String formatted;
        try {
            formatted = Javafmt.format(content);
        } catch (final Exception e) {
            return range;
        }
        if (content.equals(formatted)) {
            return range;
        }
        final Document document = source.getViewProvider().getDocument();
        if (document == null) {
            return range;
        }
        document.replaceString(0, document.getTextLength(), formatted);
        return new TextRange(0, formatted.length());
    }

    @Override
    public @Nullable String indent(@NotNull PsiFile source, int lineStartOffset) {
        return null;
    }
}
