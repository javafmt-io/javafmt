package io.javafmt.lint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LintRulesTest {

    @Test
    void defaultsRunInTheirFixedOrder() {
        assertThat(LintRules.defaults().stream().map(LintRule::name).toList())
                .containsExactly(
                        "MultipleVariableDeclarations", "LocalVarUseVar", "RedundantTypeArguments", "UnusedVariableShouldBeUnnamed",
                        "FinalLocalVariable", "FinalParameters", "FinalCatchParameter", "FinalForEachVariable", "ArrayTrailingComma",
                        "EnumTrailingComma", "ExplodeStarImports", "RemoveUnusedImports", "NeedBraces", "FallThrough", "DefaultComesLast",
                        "ModifierOrder", "RedundantModifier", "ArrayTypeStyle", "UpperEll", "ExplicitInitialization", "RedundantSuperCall",
                        "OneStatementPerLine", "EmptyStatement", "AvoidStarImport", "EqualsHashCode", "MissingSwitchDefault", "EmptyBlock",
                        "HideUtilityClassConstructor", "CovariantEquals", "StringLiteralEquality", "MemberOrder", "NewlineAtEndOfFile");
    }
}
