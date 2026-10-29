package io.javafmt.lint;

import io.javafmt.parser.ParseException;
import java.util.List;

/**
 * A lint rewrite produced source that javac can no longer parse. The file parsed on the way in, so
 * this is always a rule bug and never a problem with the user's code — which is the whole reason it
 * is not a {@link ParseException}: that is the type {@code Javafmt} reads as "the input is at
 * fault", and reporting it would blame the file for javafmt's mistake.
 */
public final class LintRewriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LintRewriteException(final List<String> rules, final ParseException cause) {
        super("a lint rewrite produced source that no longer parses; last rules to edit: " + String.join(", ", rules), cause);
    }
}
