package io.javafmt.lint;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class LintRules {

    private final List<LintRule> DEFAULTS = List.of(
            // split multi-variable declarations before FinalLocalVariable/LocalVarUseVar
            new MultipleVariableDeclarations(),
            // var inference before final insertion
            new LocalVarUseVar(),
            // diamond / witness cleanup after var inference (otherwise diamond-ified ctors
            // become un-var-convertible)
            new RedundantTypeArguments(),
            new FinalLocalVariable(),
            new FinalParameters(),
            new ArrayTrailingComma(),
            new EnumTrailingComma(),
            new ExplodeStarImports(),
            new RemoveUnusedImports(),
            new NeedBraces(),
            new FallThrough(),
            new DefaultComesLast(),
            new ModifierOrder(),
            new RedundantModifier(),
            new ArrayTypeStyle(),
            new UpperEll(),
            new ExplicitInitialization(),
            new RedundantSuperCall(),
            // split same-line statements before removing empty ones
            new OneStatementPerLine(),
            new EmptyStatement(),
            // warning-only rules
            new AvoidStarImport(),
            new EqualsHashCode(),
            new MissingSwitchDefault(),
            new EmptyBlock(),
            new HideUtilityClassConstructor(),
            new CovariantEquals(),
            new StringLiteralEquality(),
            // member reordering after every rule that reads or rewrites a member's own text, so
            // none of them has to cope with a member that has just moved
            new MemberOrder(),
            // file-level rule runs last
            new NewlineAtEndOfFile());

    public List<LintRule> defaults() {
        return DEFAULTS;
    }
}
