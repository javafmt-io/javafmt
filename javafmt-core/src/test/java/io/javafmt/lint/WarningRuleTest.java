package io.javafmt.lint;

import com.sun.source.tree.MethodInvocationTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarningRuleTest {

    @Test
    void onNodeSeesEveryCallInAChain() {
        final var source = "class Chain {\n\n    Object value = a.b().c(d.e()).f();\n}\n";
        final var warnings = WarningRule.onNode(LintContext.parseOnly(JavaParser.parseUnit(source)), MethodInvocationTree.class, _ -> true, "call");
        assertThat(warnings)
                .hasSize(4)
                .allSatisfy(d -> assertThat(d).isInstanceOf(Diagnostic.Warning.class));
    }
}
