package io.javafmt.lint;

import io.javafmt.JavafmtConfig;
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Per-rule input bundle. Always carries the {@link ParsedUnit}; carries an
 * {@link Attribution} only when the run was given a classpath. Rules ask
 * {@link #attributionFor(Class)} and upgrade their behavior when type information is
 * available; a rule the project file lists under {@code analyze.skip} gets none.
 */
public record LintContext(ParsedUnit unit,
                          Optional<Attribution> attribution,
                          int release,
                          Set<String> analyzeSkip,
                          ParsedUnit unattributedUnit,
                          Map<String, AttributedUnit> attributedRewrites) {
    public LintContext {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(attribution, "attribution");
        analyzeSkip = Set.copyOf(analyzeSkip);
        Objects.requireNonNull(unattributedUnit, "unattributedUnit");
        attributedRewrites = Map.copyOf(attributedRewrites);
    }

    public static LintContext parseOnly(final ParsedUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return new LintContext(unit, Optional.empty(), JavafmtConfig.LATEST_RELEASE, Set.of(), unit, Map.of());
    }

    public static LintContext attributed(final AttributedUnit attributed) {
        Objects.requireNonNull(attributed, "attributed");
        return attributed(attributed, JavaParser.parseUnit(attributed.unit().source()));
    }

    public static LintContext attributed(final AttributedUnit attributed, final ParsedUnit unattributed) {
        Objects.requireNonNull(attributed, "attributed");
        return new LintContext(
                attributed.unit(), Optional.of(attributed.attribution()), JavafmtConfig.LATEST_RELEASE, Set.of(), unattributed, Map.of());
    }

    public Optional<Attribution> attributionFor(final Class<? extends LintRule> rule) {
        return analyzeSkip.contains(rule.getSimpleName()) ? Optional.empty() : attribution;
    }

    public LintContext forRule(final LintRule rule) {
        return rule.usesAnalysis() || attribution.isEmpty()
                ? this
                : new LintContext(unattributedUnit, Optional.empty(), release, analyzeSkip, unattributedUnit, Map.of());
    }

    public LintContext withRelease(final int targetRelease) {
        return new LintContext(unit, attribution, targetRelease, analyzeSkip, unattributedUnit, attributedRewrites);
    }

    public LintContext skippingAnalysisFor(final Set<String> rules) {
        return new LintContext(unit, attribution, release, rules, unattributedUnit, attributedRewrites);
    }

    public LintContext reparsed(final ParsedUnit rewritten) {
        Objects.requireNonNull(rewritten, "rewritten");
        return new LintContext(rewritten, Optional.empty(), release, analyzeSkip, rewritten, Map.of());
    }

    public LintContext withAttributedRewrites(final Map<String, AttributedUnit> rewrites) {
        return new LintContext(unit, attribution, release, analyzeSkip, unattributedUnit, rewrites);
    }

    public AttributedUnit reattributed(final String rewritten, final Attribution like) {
        return Optional.ofNullable(attributedRewrites.get(rewritten)).orElseGet(() -> JavaParser.reattribute(rewritten, like));
    }
}
