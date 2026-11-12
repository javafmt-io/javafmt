package io.javafmt.lint;

import com.sun.source.tree.Tree;
import io.javafmt.parser.ChainScanner;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One rule's share of a round: the scanner that gathers what the rule needs and what the rule
 * makes of it once the walk is over. The engine hands the scanners of every rule to one {@link
 * io.javafmt.parser.FanOutScanner}, so the tree is walked once per round, not once per rule.
 */
record RuleScan(ChainScanner<Void, Void> scanner, Supplier<LintResult> result) {

    /** A scan with nothing to look for: the rule has already decided its result. */
    static RuleScan of(final LintResult result) {
        return new RuleScan(
                new ChainScanner<>() {
                    @Override
                    @Nullable
                    public Void scan(@Nullable final Tree tree, @Nullable final Void p) {
                        return null;
                    }
                },
                () -> result);
    }

    /** A scan whose scanner adds edits to the list it is given. */
    static RuleScan editing(final Function<List<LintEdit>, ChainScanner<Void, Void>> scanner) {
        return collecting(scanner, LintResult::ofEdits);
    }

    /** A scan whose scanner adds what it finds to the list it is given, for {@code finish} to turn into a result. */
    static <T> RuleScan collecting(final Function<List<T>, ChainScanner<Void, Void>> scanner, final Function<List<T>, LintResult> finish) {
        final var sink = new ArrayList<T>();
        return new RuleScan(scanner.apply(sink), () -> finish.apply(List.copyOf(sink)));
    }
}
