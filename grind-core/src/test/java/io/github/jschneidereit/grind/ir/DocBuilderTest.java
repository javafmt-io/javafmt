package io.github.jschneidereit.grind.ir;

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

import io.github.jschneidereit.grind.GrindConfig;
import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.parser.ParsedUnit;
import io.github.jschneidereit.grind.printer.Printer;

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
        return Printer.print(DocBuilder.build(JavaParser.parseUnit(source)), WIDTH);
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
        assertThatThrownBy(() -> DocBuilder.build(unit, GrindConfig.defaults()))
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
