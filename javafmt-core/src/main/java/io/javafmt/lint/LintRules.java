package io.javafmt.lint;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public final class LintRules {

    private final List<LintRule> SPLIT_VARIABLE_DECLARATIONS_BEFORE_VAR_AND_FINAL = List.of(new MultipleVariableDeclarations());

    private final List<LintRule> INFER_VAR_BEFORE_INSERTING_FINAL = List.of(new LocalVarUseVar());

    private final List<LintRule> REMOVE_TYPE_ARGUMENTS_AFTER_VAR_SO_A_DIAMOND_CANNOT_BLOCK_VAR = List.of(new RedundantTypeArguments());

    private final List<LintRule> OTHER_RULES = List.of(
            new UnusedVariableShouldBeUnnamed(), new FinalLocalVariable(), new FinalParameters(), new FinalCatchParameter(),
            new FinalForEachVariable(), new ArrayTrailingComma(), new EnumTrailingComma(), new ExplodeStarImports(), new RemoveUnusedImports(),
            new NeedBraces(), new FallThrough(), new DefaultComesLast(), new ModifierOrder(), new RedundantModifier(), new ArrayTypeStyle(),
            new UpperEll(), new ExplicitInitialization(), new RedundantSuperCall());

    private final List<LintRule> SPLIT_STATEMENTS_ON_ONE_LINE_BEFORE_REMOVING_EMPTY_ONES = List.of(new OneStatementPerLine(), new EmptyStatement());

    private final List<LintRule> WARNING_ONLY_RULES = List.of(
            new AvoidStarImport(), new EqualsHashCode(), new MissingSwitchDefault(), new EmptyBlock(), new HideUtilityClassConstructor(),
            new CovariantEquals(), new StringLiteralEquality());

    private final List<LintRule> REORDER_MEMBERS_AFTER_EVERY_RULE_THAT_READS_OR_REWRITES_A_MEMBER = List.of(new MemberOrder());

    private final List<LintRule> FILE_LEVEL_RULES_LAST = List.of(new NewlineAtEndOfFile());

    private final List<LintRule> DEFAULTS = Stream.of(
            SPLIT_VARIABLE_DECLARATIONS_BEFORE_VAR_AND_FINAL, INFER_VAR_BEFORE_INSERTING_FINAL,
            REMOVE_TYPE_ARGUMENTS_AFTER_VAR_SO_A_DIAMOND_CANNOT_BLOCK_VAR, OTHER_RULES, SPLIT_STATEMENTS_ON_ONE_LINE_BEFORE_REMOVING_EMPTY_ONES,
            WARNING_ONLY_RULES, REORDER_MEMBERS_AFTER_EVERY_RULE_THAT_READS_OR_REWRITES_A_MEMBER, FILE_LEVEL_RULES_LAST)
            .flatMap(List::stream)
            .toList();

    private final List<String> ANALYSIS_RULE_NAMES = DEFAULTS.stream().filter(LintRule::usesAnalysis).map(LintRule::name).sorted().toList();

    private final Set<String> RULE_NAMES = DEFAULTS.stream().map(LintRule::name).collect(Collectors.toUnmodifiableSet());

    public List<LintRule> defaults() {
        return DEFAULTS;
    }

    public List<String> analysisRuleNames() {
        return ANALYSIS_RULE_NAMES;
    }

    public Set<String> ruleNames() {
        return RULE_NAMES;
    }
}
