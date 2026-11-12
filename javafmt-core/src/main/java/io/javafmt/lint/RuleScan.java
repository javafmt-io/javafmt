package io.javafmt.lint;

import com.sun.source.tree.Tree;
import io.javafmt.parser.ChainScanner;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

record RuleScan(ChainScanner<Void, Void> scanner, Supplier<LintResult> result) {

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

    static RuleScan editing(final Function<List<LintEdit>, ChainScanner<Void, Void>> scanner) {
        return collecting(scanner, LintResult::ofEdits);
    }

    static <T> RuleScan collecting(final Function<List<T>, ChainScanner<Void, Void>> scanner, final Function<List<T>, LintResult> finish) {
        final var sink = new ArrayList<T>();
        return new RuleScan(scanner.apply(sink), () -> finish.apply(List.copyOf(sink)));
    }
}
