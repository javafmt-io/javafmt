package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;

import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;

import java.util.IdentityHashMap;
import java.util.List;

import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;

class FormatResultTest {

    @Test
    void formatOnInvalidSourceReturnsInputUnchanged() {
        final var garbage = "class {";
        assertThat(Javafmt.format(garbage)).isEqualTo(garbage);
    }

    @Test
    void formatWithResultOnInvalidSourceReportsParseErrorWithPosition() {
        final var garbage = "class {";
        final var result = Javafmt.formatWithResult(garbage);
        assertThat(result.output()).isEqualTo(garbage);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).isNotEmpty();
        assertThat(result.diagnostics()).allMatch(Diagnostic::isError);
        final var d = result.diagnostics().get(0);
        assertThat(d).isInstanceOf(Diagnostic.ParseError.class);
        assertThat(d.position()).isInstanceOf(Position.At.class);
        final var at = (Position.At) d.position();
        assertThat(at.line()).isEqualTo(1);
        assertThat(at.column()).isEqualTo(6);
    }

    @Test
    void formatWithResultOnValidSourceHasNoDiagnostics() {
        final var result = Javafmt.formatWithResult("class Foo { int x; }");
        assertThat(result.output()).isEqualTo("class Foo {\n    int x;\n}");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void formatWithResultOnEmptyInputIsEmpty() {
        final var result = Javafmt.formatWithResult("");
        assertThat(result.output()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void warningsDoNotFlipHasErrors() {
        final var result = new FormatResult(
            "ok",
            java.util.List.of(new Diagnostic.Warning("just fyi", new Position.At(1, 1, 0))));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void uncheckedFailureDuringBuildIsConvertedToParseError() {
        final var src = "class Foo {}\n";
        final var realUnit = JavaParser.parseUnit(src);
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
            wrapped, src, realUnit.sourcePositions(), List.of(), List.of(),
            new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());

        final var result = Javafmt.formatParsed(src, unit, JavafmtConfig.defaults());

        assertThat(result.output()).isEqualTo(src);
        assertThat(result.hasErrors()).isTrue();
        final var d = result.diagnostics().get(0);
        assertThat(d).isInstanceOf(Diagnostic.ParseError.class);
        assertThat(d.message()).contains("IllegalStateException");
        assertThat(d.message()).contains("OTHER");
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
}
