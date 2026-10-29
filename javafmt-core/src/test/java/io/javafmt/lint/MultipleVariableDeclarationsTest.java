package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

/**
 * The split replaces each separating {@code ,} with {@code ;} plus a copy of the shared prefix, so
 * every assertion here is about the resulting source rather than about the edits: what matters is
 * that the declarators and everything the author wrote around them come through untouched.
 */
class MultipleVariableDeclarationsTest {

    private static String split(final String source) {
        final var unit = JavaParser.parseUnit(source);
        return LintEngine.applyEdits(unit.source(), new MultipleVariableDeclarations().apply(LintContext.parseOnly(unit)).edits());
    }

    private static String inMethod(final String declaration) {
        return "class Fixture {\n\n    void test() {\n        " + declaration + "\n    }\n}\n";
    }

    @Test
    void twoVariablesOnSameLineAreSplit() {
        assertThat(split(inMethod("int a, b;"))).contains("int a;").contains("int b;");
    }

    @Test
    void threeVariablesAreSplit() {
        assertThat(split(inMethod("int a, b, c;")))
                .contains("int a;")
                .contains("int b;")
                .contains("int c;");
    }

    @Test
    void aCommaWithNoSpaceAfterItStillLeavesOneBeforeTheNextName() {
        // `double a,t;` split into `doublet;`, which does not parse: the copied prefix ends at the
        // type and the source supplied the separating space. java/lang/FdLibm.java writes it this
        // way twice and javafmt refused the file.
        assertThat(split(inMethod("double a,t;")))
                .contains("double a;")
                .contains("double t;");
    }

    @Test
    void aTrailingLineCommentStaysWithTheDeclarationItFollows() {
        // The comment sits after the comma, so swallowing the whitespace up to the next name carried
        // it onto the copied prefix: `private static final double // 3.06e-17[] T = {`, which does
        // not parse. java/lang/FdLibm.java writes every one of its constant tables this way.
        assertThat(split(inMethod("int a = 1,  // one\n        b = 2;")))
                .contains("int a = 1;  // one")
                .contains("int b = 2;");
    }

    @Test
    void initializerPreservedWhenSplitting() {
        assertThat(split(inMethod("int a, b = 5;")))
                .contains("int a;")
                .contains("int b = 5;");
    }

    @Test
    void separateDeclarationsProduceNoEdits() {
        final var unit = JavaParser.parseUnit(inMethod("int a;\n        int b;"));
        assertThat(new MultipleVariableDeclarations().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }

    @Test
    void cStyleArraySuffixesStayWithTheirOwnDeclarator() {
        assertThat(split(inMethod("int a[], b[];")))
                .contains("int a[];")
                .contains("int b[];");
    }

    @Test
    void cStyleSuffixOnOnlyOneDeclaratorIsNotSpreadToTheOthers() {
        assertThat(split(inMethod("int a, b[];")))
                .contains("int a;")
                .contains("int b[];");
    }

    @Test
    void javaStyleBaseDimensionsAreKeptOnEveryDeclarator() {
        assertThat(split(inMethod("int[] a, b[];")))
                .contains("int[] a;")
                .contains("int[] b[];");
    }

    @Test
    void fieldMultipleDeclarationsSplit() {
        assertThat(split("class Fixture {\n\n    int x, y;\n}\n"))
                .contains("int x;")
                .contains("int y;");
    }

    @Test
    void aGroupCarryingAnInteriorCommentIsStillSplitAndKeepsTheComment() {
        // The rule used to decline here, because it rebuilt the declaration from modifiers, type,
        // names and initializers and had nowhere to put the comment. Eleven files of java.base then
        // reached the renderer as groups it could not print, and came back refused.
        assertThat(split(inMethod("int a = 1, /* keep me */ b = 2;")))
                .contains("int a = 1;")
                .contains("/* keep me */")
                .contains("b = 2;");
    }

    @Test
    void aCommentBeforeEachDeclaratorSurvivesTheSplit() {
        final var source = "class Fixture {\n\n    static final byte\n        /* first */\n        A = 0,\n        /* second */\n        B = 1;\n}\n";
        assertThat(split(source))
                .contains("/* first */")
                .contains("/* second */")
                .contains("A = 0;")
                .contains("B = 1;");
    }

    @Test
    void aCommentInsideTheSharedPrefixStopsTheSplit() {
        // The prefix is copied once per declaration, so a comment inside it would come out several
        // times over.
        final var unit = JavaParser.parseUnit("class Fixture {\n\n    private /* why */ static int a = 1, b = 2;\n}\n");
        assertThat(new MultipleVariableDeclarations().apply(LintContext.parseOnly(unit)).edits()).isEmpty();
    }

    @Test
    void aCommentBetweenTheTypeAndTheFirstNameDoesNotStopTheSplit() {
        // That comment belongs to the first declarator, not to the shared prefix, so it travels
        // with it. Reading it as neither left eleven files of java.base unsplit.
        assertThat(split(inMethod("int /* which */ a = 1, b = 2;")))
                .contains("/* which */")
                .contains("int b = 2;");
    }

    @Test
    void aCommaInsideTheTypeArgumentsIsNotASeparator() {
        // `Node<K, V> first, e;` has two commas and only the second separates anything.
        assertThat(split(inMethod("Node<K, V> first, e;")))
                .contains("Node<K, V> first;")
                .contains("Node<K, V> e;");
    }

    @Test
    void aCommaInsideAnInitializerIsNotASeparator() {
        assertThat(split(inMethod("int a = f(1, 2), b = 3;")))
                .contains("int a = f(1, 2);")
                .contains("int b = 3;");
    }

    @Test
    void aCommaInsideACommentIsNotASeparator() {
        assertThat(split(inMethod("int a = 1 /* one, two */, b = 2;")))
                .contains("int a = 1 /* one, two */;")
                .contains("int b = 2;");
    }
}
