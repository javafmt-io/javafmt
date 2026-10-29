package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.MethodInvocationTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

class WarningRuleTest {

    @Test
    void onNodeSeesEveryCallInAChain() {
        final var source = "class Chain {\n\n    Object value = a.b().c(d.e()).f();\n}\n";
        final var warnings = WarningRule.onNode(
                LintContext.parseOnly(JavaParser.parseUnit(source)),
                MethodInvocationTree.class,
                call -> true,
                "call");
        assertThat(warnings)
                .hasSize(4)
                .allSatisfy(d -> assertThat(d).isInstanceOf(Diagnostic.Warning.class));
    }
}
