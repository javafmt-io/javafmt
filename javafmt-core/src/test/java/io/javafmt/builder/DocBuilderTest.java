package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;

import io.javafmt.JavafmtConfig;
import io.javafmt.doc.Doc;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.printer.Printer;

import java.util.IdentityHashMap;
import java.util.List;

import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration smoke tests — one per DocBuilder dispatch path.
 * Exhaustive rendering behaviour lives in the per-renderer test classes.
 */
class DocBuilderTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)));
    }

    @Test
    void emptyClass_rendersOnOneLine() {
        assertThat(format("class Foo {}")).isEqualTo("class Foo {}");
    }

    @Test
    void emptyInterface_rendersOnOneLine() {
        assertThat(format("interface Foo {}")).isEqualTo("interface Foo {}");
    }

    @Test
    void emptyRecord_rendersOnOneLine() {
        assertThat(format("record Foo() {}")).isEqualTo("record Foo() {}");
    }

    @Test
    void emptyEnum_rendersOnOneLine() {
        assertThat(format("enum Foo {}")).isEqualTo("enum Foo {}");
    }

    @Test
    void singleImport_sortedAboveClass() {
        assertThat(format("import java.util.List;\nclass Foo {}"))
            .isEqualTo("import java.util.List;\n\nclass Foo {}");
    }

    @Test
    void field_isIndentedInsideClass() {
        assertThat(format("class Foo { int x; }"))
            .isEqualTo("class Foo {\n    int x;\n}");
    }

    @Test
    void method_isIndentedInsideClass() {
        assertThat(format("class Foo { void bar() {} }"))
            .isEqualTo("class Foo {\n    void bar() {}\n}");
    }

    @Test
    void returnStatement_renderedInsideMethod() {
        assertThat(format("class Foo { int bar() { return 42; } }"))
            .isEqualTo("class Foo {\n    int bar() {\n        return 42;\n    }\n}");
    }

    @Test
    void expressionStatement_renderedInsideMethod() {
        assertThat(format("class Foo { void bar() { doIt(); } }"))
            .isEqualTo("class Foo {\n    void bar() {\n        doIt();\n    }\n}");
    }

    @Test
    void switchExpression_renderedInsideMethod() {
        assertThat(format("class Foo { String bar(int x) { return switch (x) { case 1 -> \"one\"; default -> \"other\"; }; } }"))
            .isEqualTo("class Foo {\n    String bar(int x) {\n        return switch (x) {\n            case 1 -> \"one\";\n            default -> \"other\";\n        };\n    }\n}");
    }

    @Test
    void switchStatement_renderedInsideMethod() {
        assertThat(format("class Foo { void bar(int x) { switch (x) { case 1 -> doIt(); default -> doOther(); } } }"))
            .isEqualTo("class Foo {\n    void bar(int x) {\n        switch (x) {\n            case 1 -> doIt();\n            default -> doOther();\n        }\n    }\n}");
    }

    @Test
    void reduce_throwsIllegalStateException_signalingUnreachableMergePath() {
        final var builder = new DocBuilder(JavaParser.parseUnit("class Foo {}"), JavafmtConfig.defaults());
        assertThatThrownBy(() -> builder.reduce(new Doc.Text("a"), new Doc.Text("b")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unexpected tree merge");
    }

    @Test
    void unhandledTreeKindThrowsWithKindAndNodeText() {
        final var realUnit = JavaParser.parseUnit("class Foo {}");
        final var synthetic = new Tree() {
            @Override
            public Kind getKind() {
                return Kind.OTHER;
            }

            @Override
            public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
                return visitor.visitOther(this, data);
            }

            @Override
            public String toString() {
                return "<synthetic-OTHER-node>";
            }
        };
        final var wrapped = new SyntheticCompilationUnit(realUnit.tree(), synthetic);
        final var unit = new ParsedUnit(
            wrapped, "", realUnit.sourcePositions(), List.of(), List.of(),
            new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
        assertThatThrownBy(() -> DocBuilder.build(unit, JavafmtConfig.defaults()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OTHER")
            .hasMessageContaining("<synthetic-OTHER-node>");
    }

    private record SyntheticCompilationUnit(CompilationUnitTree real, Tree decl) implements CompilationUnitTree {

        @Override
        public List<? extends AnnotationTree> getPackageAnnotations() {
            return List.of();
        }

        @Override
        public @org.jspecify.annotations.Nullable ExpressionTree getPackageName() {
            return null;
        }

        @Override
        public @org.jspecify.annotations.Nullable PackageTree getPackage() {
            return null;
        }

        @Override
        public List<? extends ImportTree> getImports() {
            return List.of();
        }

        @Override
        public List<? extends Tree> getTypeDecls() {
            return List.of(decl);
        }

        @Override
        public @org.jspecify.annotations.Nullable ModuleTree getModule() {
            return null;
        }

        @Override
        public LineMap getLineMap() {
            return real.getLineMap();
        }

        @Override
        public JavaFileObject getSourceFile() {
            return real.getSourceFile();
        }

        @Override
        public Kind getKind() {
            return Kind.COMPILATION_UNIT;
        }

        @Override
        public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
            return visitor.visitCompilationUnit(this, data);
        }
    }

    @Nested
    class StatementSmoke {

        @Test
        void tryCatch_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { try { doIt(); } catch (Exception e) { handle(e); } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        try {\n            doIt();\n        } catch (Exception e) {\n            handle(e);\n        }\n    }\n}");
        }

        @Test
        void ifElse_renderedInsideMethod() {
            assertThat(format("class Foo { void bar(boolean b) { if (b) { doIt(); } else { doOther(); } } }"))
                .isEqualTo("class Foo {\n    void bar(boolean b) {\n        if (b) {\n            doIt();\n        } else {\n            doOther();\n        }\n    }\n}");
        }

        @Test
        void forLoop_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { for (int i = 0; i < 10; i++) { doIt(); } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        for (int i = 0; i < 10; i++) {\n            doIt();\n        }\n    }\n}");
        }

        @Test
        void whileLoop_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { while (cond()) { doIt(); } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        while (cond()) {\n            doIt();\n        }\n    }\n}");
        }

        @Test
        void doWhileLoop_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { do { doIt(); } while (cond()); } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        do {\n            doIt();\n        } while (cond());\n    }\n}");
        }

        @Test
        void lambda_renderedAsFieldInitializer() {
            assertThat(format("class Foo { Runnable r = () -> doIt(); }"))
                .isEqualTo("class Foo {\n    Runnable r = () -> doIt();\n}");
        }

        @Test
        void throwStatement_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { throw new RuntimeException(); } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        throw new RuntimeException();\n    }\n}");
        }

        @Test
        void synchronizedBlock_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { synchronized (this) { doIt(); } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        synchronized (this) {\n            doIt();\n        }\n    }\n}");
        }

        @Test
        void labeledStatement_renderedInsideMethod() {
            assertThat(format("class Foo { void bar() { outer: while (true) { break outer; } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        outer:\n        while (true) {\n            break outer;\n        }\n    }\n}");
        }

        @Test
        void assertStatement_renderedInsideMethod() {
            assertThat(format("class Foo { void bar(int x) { assert x > 0; } }"))
                .isEqualTo("class Foo {\n    void bar(int x) {\n        assert x > 0;\n    }\n}");
        }

        @Test
        void continueStatement_renderedInsideLoop() {
            assertThat(format("class Foo { void bar() { while (true) { continue; } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        while (true) {\n            continue;\n        }\n    }\n}");
        }

        @Test
        void breakStatement_renderedInsideLoop() {
            assertThat(format("class Foo { void bar() { while (true) { break; } } }"))
                .isEqualTo("class Foo {\n    void bar() {\n        while (true) {\n            break;\n        }\n    }\n}");
        }
    }

    @Nested
    class MemberGrouping {

        @Test
        void fieldThenMethod_haveBlankLineBetweenThem() {
            assertThat(format("class Foo { int x; void bar() {} }"))
                .isEqualTo("class Foo {\n    int x;\n\n    void bar() {}\n}");
        }

        @Test
        void twoFields_haveBlankLineBetweenThem() {
            assertThat(format("class Foo { int x; int y; }"))
                .isEqualTo("class Foo {\n    int x;\n\n    int y;\n}");
        }
    }
}
