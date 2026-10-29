package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import org.junit.jupiter.api.Test;

class RedundantSuperCallTest {

    private static String format(final String source) {
        return Javafmt.format(source);
    }

    @Test
    void regularConstructor_removesNoArgSuperCall() {
        assertThat(format("class Foo { Foo() { super(); } }"))
                .isEqualTo("class Foo {\n\n    Foo() {}\n}\n");
    }

    @Test
    void regularConstructor_withOtherStatements_removesOnlySuperCall() {
        assertThat(format("class Foo { Foo() { super(); doSetup(); } }"))
                .isEqualTo("class Foo {\n\n    Foo() {\n        doSetup();\n    }\n}\n");
    }

    @Test
    void recordCompactConstructor_removesInjectedSuperCall() {
        assertThat(format("record Foo(int x) { public Foo { super(); } }"))
                .isEqualTo("record Foo(int x) {\n\n    public Foo {}\n}\n");
    }

    @Test
    void superWithArgs_isPreserved() {
        assertThat(format("class Foo extends Bar { Foo(int x) { super(x); } }"))
                .isEqualTo("class Foo extends Bar {\n\n    Foo(final int x) {\n        super(x);\n    }\n}\n");
    }

    @Test
    void thisCall_isPreserved() {
        assertThat(format("class Foo { Foo() { this(0); } Foo(int x) {} }"))
                .isEqualTo("class Foo {\n\n    Foo() {\n        this(0);\n    }\n\n    Foo(final int x) {}\n}\n");
    }

    @Test
    void superNotFirstStatement_isPreserved() {
        // super() after another statement is already a compile error, but we don't touch it
        assertThat(format("class Foo { Foo() { doSetup(); } }"))
                .isEqualTo("class Foo {\n\n    Foo() {\n        doSetup();\n    }\n}\n");
    }

    @Test
    void trailingCommentOnTheSuperCallSurvives() {
        // Deleting the whole line took the author's comment with it, and nothing could see that:
        // the output verifier compares against the post-lint source, where the comment is already
        // gone. Drop the statement only and leave the line for the comment.
        assertThat(format("class Foo {\n\n    Foo() {\n        super(); // keep this\n    }\n}\n")).contains("keep this");
    }
}
