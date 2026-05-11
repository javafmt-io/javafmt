package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JavafmtExternalFormatProcessorTest extends LightJavaCodeInsightFixtureTestCase {

    private JavafmtExternalFormatProcessor processor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        processor = new JavafmtExternalFormatProcessor();
    }

    public void testActiveForJavaFile() {
        final var file = myFixture.configureByText("Foo.java", "class Foo {}");
        assertThat(processor.activeForFile(file)).isTrue();
    }

    public void testNotActiveForNonJavaFile() {
        final var file = myFixture.configureByText("notes.txt", "hello");
        assertThat(processor.activeForFile(file)).isFalse();
    }

    public void testFormatsWholeFile() {
        final var file = myFixture.configureByText("Foo.java", "class Foo { int x; }");
        final var range = new TextRange(0, file.getTextLength());
        final var result = WriteCommandAction.runWriteCommandAction(
                getProject(),
                (Computable<TextRange>) () -> processor.format(file, range, false, false, false, -1));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertThat(result).isNotNull();
        assertThat(myFixture.getEditor().getDocument().getText()).isEqualTo("class Foo {\n    int x;\n}");
    }

    public void testUnchangedFileReturnsOriginalRange() {
        // Format once to get the canonical form, then verify a second format is a no-op.
        final var file = myFixture.configureByText("Foo.java", "class Foo { int x; }");
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                (Computable<TextRange>) () -> processor.format(
                        file, new TextRange(0, file.getTextLength()), false, false, false, -1));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        final var canonical = file.getText();

        // Second format of already-canonical content must return the same range unchanged.
        final TextRange second = WriteCommandAction.runWriteCommandAction(
                getProject(),
                (Computable<TextRange>) () -> processor.format(
                        file, new TextRange(0, file.getTextLength()), false, false, false, -1));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertThat(second).isEqualTo(new TextRange(0, canonical.length()));
        assertThat(file.getText()).isEqualTo(canonical);
    }

    public void testPartialRangeFallsBack() {
        final var file = myFixture.configureByText("Foo.java", "class Foo { int x; }");
        final var partial = new TextRange(0, 5);
        // Partial ranges fall back to IntelliJ's built-in formatter; no write-action needed.
        final var result = processor.format(file, partial, false, false, false, -1);
        assertThat(result).isNull();
    }

    public void testIndentReturnsNull() {
        final var file = myFixture.configureByText("Foo.java", "class Foo {}");
        assertThat(processor.indent(file, 0)).isNull();
    }
}
