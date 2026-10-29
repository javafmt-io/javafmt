package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;
import java.util.IdentityHashMap;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * The output-verification guard: when enabled, javafmt refuses to emit output whose token
 * stream or comments differ from the post-lint source, returning the input unchanged with a
 * {@link Diagnostic.InternalError} instead of silently dropping the user's code.
 */
class JavafmtVerifyOutputTest {

    private static final String SOURCE = "class Fixture {\n\n    int x;\n}\n";

    private static final String SOURCE_WITH_NOTE = SOURCE + "// a note the renderer never sees\n";

    /**
     * Builds a unit whose tree and comment channels cover {@link #SOURCE} while its source
     * text also carries a trailing comment. The renderer therefore emits faithful output for
     * everything it knows about and drops the comment — exactly the shape of a comment
     * landing in a position {@code CommentAttacher} doesn't cover, without depending on any
     * particular open renderer gap.
     */
    private static ParsedUnit unitThatLosesATrailingComment() {
        final var parsed = JavaParser.parseUnit(SOURCE);
        return new ParsedUnit(
                parsed.tree(),
                SOURCE_WITH_NOTE,
                parsed.sourcePositions(),
                List.of(),
                List.of(),
                new IdentityHashMap<>(),
                new IdentityHashMap<>(),
                new IdentityHashMap<>(),
                new IdentityHashMap<>());
    }

    @Test
    void verificationIsOnByDefault() {
        // The guard returns the file unchanged with an InternalError rather than emitting output
        // that lost something. That is only worth having if it is on for everyone by default.
        assertThat(JavafmtConfig.defaults().verifyOutput()).isTrue();
    }

    @Test
    void withVerificationOffTheLostCommentGoesUnreported() {
        final var config = JavafmtConfig.defaults().withVerifyOutput(false);
        final var result = BatchFormatter.formatParsed(SOURCE_WITH_NOTE, unitThatLosesATrailingComment(), config);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.output()).doesNotContain("a note the renderer never sees");
    }

    @Test
    void withVerificationOnTheLostCommentBecomesAnInternalError() {
        final var config = JavafmtConfig.defaults().withVerifyOutput(true);
        final var result = BatchFormatter.formatParsed(SOURCE_WITH_NOTE, unitThatLosesATrailingComment(), config);
        assertThat(result.diagnostics())
                .singleElement()
                .isInstanceOf(Diagnostic.InternalError.class);
        assertThat(result.diagnostics().getFirst().message()).contains("comment");
    }

    @Test
    void withVerificationOnAFailedCheckReturnsTheInputUnchanged() {
        final var config = JavafmtConfig.defaults().withVerifyOutput(true);
        final var result = BatchFormatter.formatParsed(SOURCE_WITH_NOTE, unitThatLosesATrailingComment(), config);
        assertThat(result.output()).isEqualTo(SOURCE_WITH_NOTE);
    }

    @Test
    void verificationLetsFaithfulFormattingThrough() {
        final var config = JavafmtConfig.defaults().withVerifyOutput(true);
        final var messy = "class Fixture{int x;void m(){if(x>0){x=1;}}}\n";
        final var result = Javafmt.formatWithResult(messy, config);
        assertThat(result.diagnostics()).noneMatch(d -> d instanceof Diagnostic.InternalError);
        assertThat(result.output()).contains("int x;");
    }

    @Test
    void verificationToleratesLintRewrites() {
        // NeedBraces, FinalParameters and FinalLocalVariable all change the token stream on
        // purpose. Verification compares against the post-lint source, so they pass.
        final var config = JavafmtConfig.defaults().withVerifyOutput(true);
        final var source = "class Fixture {\n    void m(int n) {\n        String s = \"hi\";\n        if (n > 0) log(s);\n    }\n\n    void log(String s) {}\n}\n";
        final var result = Javafmt.formatWithResult(source, config);
        assertThat(result.diagnostics()).noneMatch(d -> d instanceof Diagnostic.InternalError);
        assertThat(result.output())
                .contains("void m(final int n)")
                .contains("final String s = \"hi\";")
                .contains("if (n > 0) {");
    }

    private static final String SUPER_CALL_WITH_NOTE = "class Fixture {\n\n    Fixture() {\n        super(); // keep this\n    }\n}\n";

    private static final String SUPER_CALL_LINTED_AWAY = "class Fixture {\n\n    Fixture() {\n    }\n}\n";

    private static final String SUPER_CALL_PRINTED = "class Fixture {\n\n    Fixture() {}\n}\n";

    @Test
    void aCommentTheLintPassDroppedBecomesAnInternalError() {
        // The ordinary check holds the output against the post-lint source, where a comment a rule
        // deleted is already missing from both sides — which is how two rules deleted comments for
        // as long as they did. Comments are held against what the user actually wrote instead.
        assertThat(FormatStages.verifyOutput(JavafmtConfig.defaults(), SUPER_CALL_WITH_NOTE, SUPER_CALL_LINTED_AWAY, SUPER_CALL_PRINTED))
                .hasValueSatisfying(message -> assertThat(message).contains("lint rule dropped one of your comments").contains("keep this"));
    }

    @Test
    void theLintCommentCheckSaysNothingWhenTheCommentSurvives() {
        // The rule declined to delete the line, so the comment is in the post-lint source and in
        // the output. Neither check has anything to say.
        final var linted = "class Fixture {\n\n    Fixture() {\n        // keep this\n    }\n}\n";
        final var printed = "class Fixture {\n\n    Fixture() {\n        // keep this\n    }\n}\n";
        assertThat(FormatStages.verifyOutput(JavafmtConfig.defaults(), SUPER_CALL_WITH_NOTE, linted, printed)).isEmpty();
    }

    @Test
    void verificationOffSkipsTheLintCommentCheckToo() {
        final var config = JavafmtConfig.defaults().withVerifyOutput(false);
        assertThat(FormatStages.verifyOutput(config, SUPER_CALL_WITH_NOTE, SUPER_CALL_LINTED_AWAY, SUPER_CALL_PRINTED)).isEmpty();
    }

    @Test
    void aRefusalSaysWhoseFaultItIsWhatHappenedToTheFileAndWhereToReportIt() {
        // Reaching a refusal means javafmt got it wrong. A bare `error:` naming the tokens that
        // differ reads, in a build log, as a complaint about the user's code — which is backwards,
        // and leaves them with no idea whether the file on disk was damaged.
        final var result = BatchFormatter.formatParsed(SOURCE_WITH_NOTE, unitThatLosesATrailingComment(), JavafmtConfig.defaults());
        assertThat(result.diagnostics())
                .singleElement()
                .extracting(Diagnostic::message, InstanceOfAssertFactories.STRING)
                .contains("javafmt bug")
                .contains("left unchanged")
                .contains("Your code is fine")
                .contains("https://github.com/javafmt-io/javafmt/issues");
    }

    @Test
    void aRefusalStillCarriesTheDetailForWhoeverFixesIt() {
        final var result = BatchFormatter.formatParsed(SOURCE_WITH_NOTE, unitThatLosesATrailingComment(), JavafmtConfig.defaults());
        assertThat(result.diagnostics().getFirst().message()).contains("a note the renderer never sees");
    }
}
