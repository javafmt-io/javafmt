# Comment policy: keep three shapes, drop the rest

## Intent

Comments are the largest source of incidental complexity in `javafmt-core`. javafmt promises to keep every comment beside the exact token it was written next to, so `CommentAttacher` (1,102 lines) files each comment into one of six buckets, `CommentDocs` has 41 call sites in 15 renderer files, and 25 places in 10 files ask whether a Doc ends in a line comment. `docs/rules.md` spends two dense paragraphs listing supported positions, and the rest of the formatter bends around them.

The owner does not value comments and wants the formatter to stop paying for them. The contract becomes small: javafmt keeps comments in three shapes and deletes every other comment, with a warning.

## Goals

- Delete most of `CommentAttacher`, the `interior`, `tail`, `braceLine` and `loose` channels of `ParsedUnit`, and the renderer code that serves them.
- A short rule a user can hold in their head, written in `docs/rules.md`.
- No silent data loss: each dropped comment produces a warning with line and column. Formatting still succeeds.
- Output stays idempotent: `format(format(x)) == format(x)`.

## Non-goals

- Changing layout rules (width, chains, lambdas). That is a separate project.
- Re-wrapping or rewriting comment text. Kept comments stay verbatim, as today.
- Interpreting tool directives. `// NOPMD`, `// noinspection` and `// @formatter:off` are ordinary comments, kept or dropped by position like any other.

## Anti-goals

- Refusing a file because of a comment. The "leave the file as written" errors for unsupported comments go away.
- Dropping a comment that is in a kept shape.

## The rule

A comment survives in exactly these shapes.

**Own-line.** The comment starts its line (`//`, `/* */` or `/** */`, including Javadoc) and the next token begins one of these sites:
- the package declaration, an import, or a top-level type
- a member of a class, interface, enum, record or annotation body
- an enum constant
- a statement in a block
- a `case` or `default` label
- or the comment sits before the `}` that closes a block, body, `switch` or enum

File-header comments (above `package`) and file-footer comments (after the last declaration) count as own-line.

**Trailing.** The comment is at the end of a line that holds code, and the code before it ends one of:
- a statement or declaration (`;`)
- an annotation on its own line (`@Override // why`)
- the `{` that opens a block or body
- a `}`
- a `case` label's `:` or `->`
- an enum constant (with or without its `,` or `;`)

**Everything else is dropped**, with a warning. That includes a comment:
- inside an expression, argument list, parameter list, chain, `throws`/`implements`/`permits` list, array initializer or annotation argument list
- between two tokens on a line, as in `call(/* count */ 3)` or `} else /* otherwise */ {`
- on its own line before something that is not a site, such as an argument, a chain link or a binary operator
- trailing after an argument, parameter, chain link or operator

A kept own-line comment stays on its own line, and a kept trailing comment stays at the end of its line. javafmt never writes code after a comment on the same line (unchanged).

## Constraints

- Kept comments keep their text verbatim. Continuation lines of a block comment reindent to the comment's output column (unchanged).
- Lint rules rewrite source text and must keep working. They must not see comments that will be dropped.
- Performance matters. The common case (no dropped comment) must not add a parse.
- TDD per `CLAUDE.md`: a failing fixture first. Fixtures use `input.java` and `expected.java`, with idempotent fixtures where the contract is "this shape is preserved".
- `docs/rules.md` is updated in the same commit as any change to what javafmt produces.

## Approaches considered

**A. Classify and strip before lint (chosen).** After the first parse, a classification step applies the rule above using AST positions. It blanks every dropped comment out of the source text (replacing it with spaces, or nothing for a whole own-line comment, per the builder's call) and emits a warning for each. If nothing is dropped, the original source flows on with no extra parse. If something is dropped, the stripped source is re-parsed once. The rest of the pipeline (lint fixed point, attacher, `DocBuilder`, `RoundTripVerifier`) only ever sees kept comments. The attacher shrinks to two channels, `leading` and `trailing`, because every surviving comment is own-line before a site or trailing after one.

**B. Attach as today, discard afterwards.** Smallest diff, but the attacher and the renderer comment handling stay. Almost no code is removed, so it does not meet the goal.

**C. Comments as tokens inside the Doc.** Largest simplification on paper, but every renderer's comment placement is rewritten at once. Highest risk, and it needs the whole formatter's tests to move together.

Chosen: A, because it removes nearly all the comment complexity without touching layout code.

## Parts

1. **Classifier.** New component in `io.javafmt.parser`. Input: parsed unit with all comments. Output: the kept comments and a list of dropped ones with positions. Pure and deterministic.
2. **Strip stage.** Runs in `FormatStages` after the first parse and before lint. Produces the stripped source and one `Diagnostic.Warning` per drop, with line and column and the first characters of the comment.
3. **Attacher.** Two channels, `leading` and `trailing` per site, plus file header and footer. Delete `Slot`, `Reach`, `Kind.INTERIOR`, `TAIL`, `BRACE_LINE`, `BEFORE_BRACE`, `LOOSE`, and `crossesSeparator`.
4. **`ParsedUnit`.** Drop `interior`, `tail`, `braceLine` and `loose`, with their accessors.
5. **Renderers.** Remove the code that reads those channels. A trailing comment after `{` or `}` becomes ordinary trailing. Keep the check that a Doc ending in a `//` comment cannot have code glued after it, wherever a kept trailing `//` can still precede one.
6. **`RoundTripVerifier`.** Compare tokens, and compare comments against the stripped source's comments. A dropped comment is no longer an internal error.
7. **Docs.** Replace the "Javadoc and comments" section of `docs/rules.md` with the three-shape rule and the warning. Update `ARCHITECTURE.md`'s file-formatting diagram to show the strip stage.
8. **Tests.** Delete or rewrite `CommentPlacementTest`, `CommentAuditTest`, `MisplacedCommentTest`, `CommentAttacherTest` and the 42 comment fixtures, as they stop being true. Add fixtures for each kept shape and for each drop category, including a warning-count assertion. Add idempotency for a file containing dropped comments.

## Calls I made

- A comment on its own line before an argument, an array element, a parameter or a chain link is dropped. Only the site list above is kept.
- A trailing comment after an argument, chain link or enum-constant list item is dropped, except after an enum constant.
- A `//` comment after `{` of a lambda, `switch`, `if`, `else`, loop, `try` or class body is kept as trailing (it follows a `{`).
- The warning text and exact `Diagnostic` shape are for the builder.

## Calls settled after the builder check

- **Surfacing.** A drop is a `Diagnostic.Warning` on the existing channel, which Gradle, the daemon (IntelliJ and VS Code), Spotless and the CLI's JSON output already carry. No new transport. `--check` and `spotlessCheck` fail on such a file for the usual reason: the formatted output differs from the input. The warning is not itself an error. The builder confirms the CLI's plain-text path prints it.
- **Lambda and anonymous-class bodies.** The `{` of any block or body is a site, even in argument position, so `foo(x -> { // why` keeps its comment. A trailing comment after the `}` that closes a lambda or anonymous class inside an expression is dropped. One after the `;` that ends the enclosing statement is kept.
- **Annotations and modifiers.** An own-line comment before the first annotation or modifier of a member is kept, because the member site starts there. An own-line comment between annotations, or between the last annotation and the rest of the declaration, is dropped.
- **Continuation keywords.** An own-line comment before `else`, `catch`, `finally` or the `while` of a do-while is dropped. So is a trailing comment after the `}` that precedes one of them, since the formatter joins them onto one line.
- **Trailing on moved lines.** A trailing comment attaches to the last token of the statement, declaration or `{` it follows and stays at the end of that token's output line. With several statements on one line, only the last gets the comment. With a multi-line expression such as `x = foo(\n a,\n b); // c`, the comment follows the statement's final `;`.
- **Positions.** The classifier computes every warning position against the original source, before any stripping, and the strip runs once at the first parse in every format path, including the batch path with a classpath. Blanking keeps offsets stable so lint and the attacher see consistent positions. A dropped own-line comment must not leave a whitespace-only line that the blank-line rules would read as a blank line.
- **Other modules.** The plan includes a sweep for fixtures in IntelliJ, VS Code, Spotless, Gradle and Maven and for the corpus and round-trip tooling that assert on comment behaviour. The broad corpus will now produce warnings, so its checker must expect them.

## Left to the builder

- Whether to blank dropped comments to spaces or delete them, and how to keep line numbers stable for later warnings.
- How to share position-to-site logic between the classifier and the attacher so the two cannot disagree.
- The order of deletions across commits. Each commit keeps the build green.
- Which of the 42 comment fixtures are rewritten and which are deleted.
