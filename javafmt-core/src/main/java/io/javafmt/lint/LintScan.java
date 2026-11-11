package io.javafmt.lint;

import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The scan-from-root template shared by rules whose apply() is one visitor over the unit. */
@UtilityClass
final class LintScan {

    /** Runs {@code scanner(sink)} over the unit and returns what the sink collected. */
    <T> List<T> collect(final LintContext context, final Function<List<T>, ChainScanner<Void, Void>> scanner) {
        return collect(context.unit(), scanner);
    }

    /** Runs {@code scanner(sink)} over the unit and returns what the sink collected. */
    <T> List<T> collect(final ParsedUnit unit, final Function<List<T>, ChainScanner<Void, Void>> scanner) {
        final var collected = new ArrayList<T>();
        scanner.apply(collected).scan(unit.tree(), null);
        return List.copyOf(collected);
    }

    LintResult edits(final LintContext context, final Function<List<LintEdit>, ChainScanner<Void, Void>> scanner) {
        return LintResult.ofEdits(collect(context, scanner));
    }
}
