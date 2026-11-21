package io.javafmt.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.javafmt.builder.RendererTestSupport.formatWithJavafmt;
import static org.assertj.core.api.Assertions.assertThat;

class ModuleRendererTest {

    static Stream<Arguments> modules() {
        return Stream.of(
                Arguments.of("module foo.bar {}", "module foo.bar {}"),
                Arguments.of("open module foo.bar {}", "open module foo.bar {}"),
                Arguments.of("module foo.bar { requires java.base; }", "module foo.bar {\n    requires java.base;\n}"),
                Arguments.of("module foo.bar { requires static java.compiler; }", "module foo.bar {\n    requires static java.compiler;\n}"),
                Arguments.of("module foo.bar { requires transitive java.desktop; }", "module foo.bar {\n    requires transitive java.desktop;\n}"),
                Arguments.of("module foo.bar { exports a.b; }", "module foo.bar {\n    exports a.b;\n}"),
                Arguments.of("module foo.bar { exports a.b to m1, m2; }", "module foo.bar {\n    exports a.b to m1, m2;\n}"),
                Arguments.of("module foo.bar { exports a.b to zeta, alpha, beta; }", "module foo.bar {\n    exports a.b to zeta, alpha, beta;\n}"),
                Arguments.of("module foo.bar { opens a.b to zeta, alpha; }", "module foo.bar {\n    opens a.b to zeta, alpha;\n}"),
                Arguments.of("module foo.bar { provides a.S with b.Zeta, b.Alpha; }", "module foo.bar {\n    provides a.S with b.Zeta, b.Alpha;\n}"),
                Arguments.of("module foo.bar { uses a.Service; }", "module foo.bar {\n    uses a.Service;\n}"),
                Arguments.of("module foo.bar { provides a.Service with a.Impl; }", "module foo.bar {\n    provides a.Service with a.Impl;\n}"),
                Arguments.of(
                        "module foo.bar { provides a.Service with z.Z, a.A, m.M; }",
                        "module foo.bar {\n    provides a.Service with z.Z, a.A, m.M;\n}"),
                Arguments.of("module foo.bar { requires java.base; exports a.b; uses a.Service; }", """
                                                                                                    module foo.bar {
                                                                                                        requires java.base;
                                                                            
                                                                                                        exports a.b;
                                                                            
                                                                                                        uses a.Service;
                                                                                                    }"""),
                Arguments.of("module foo.bar { requires z; requires a; requires m; }", """
                                                                                       module foo.bar {
                                                                                           requires z;
                                                                                           requires a;
                                                                                           requires m;
                                                                                       }"""));
    }

    @ParameterizedTest
    @MethodSource("modules")
    void formatsTheModuleDeclaration(final String source, final String expected) {
        assertThat(formatWithJavafmt(source)).isEqualTo(expected);
    }

    @Test
    void exportsTo_longList_breaksWithEightSpaceContinuation() {
        final var source = "module foo.bar { exports my.very.detailed.api to "
            + "moduleAlpha01, moduleBeta02, moduleGamma03, moduleDelta04, moduleEpsilon05, "
            + "moduleZeta06, moduleEta07, moduleTheta08, moduleIota09, moduleKappa10; }";
        final var formatted = formatWithJavafmt(source);
        assertThat(formatted).contains("\n            moduleAlpha01");
        formatted.lines().forEach(line -> assertThat(line.length()).isLessThanOrEqualTo(150));
    }
}
