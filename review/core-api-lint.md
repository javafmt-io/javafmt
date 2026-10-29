# javafmt-core parser / lint / public API review

## Summary

Not ready for a public release. The architecture is sound — the parse/lint/render split is clean,
the lint engine's fixed-point loop with re-parse validation is the right design, and the Checkstyle
oracle suite plus `LintCompileSafetyTest` show real testing discipline. But three defects will bite
day-one users, and I reproduced all three against the checked-in build (`javafmt-cli-…-all.jar`,
newer than every source file at `2f40ba7`).

Top 3:

1. **P0 — javafmt hangs (infinite loop, 100% CPU, forever) on `@SuppressWarnings("x") // comment`
   above an interface method.** Two files in the JDK's own `java.base` trigger it
   (`java/util/stream/Stream.java`, `java/lang/invoke/BoundMethodHandle.java`). A whole run
   produces *zero* output and never terminates. `RedundantModifier.java:96-120`.
2. **P0 — `LocalVarUseVar` emits code that does not compile** for
   `Comparator<String> c = Comparator.comparing(String::length);` → `var c =
   Comparator.<String>comparing(...)`. Exit code 0, no diagnostic. `LocalVarUseVar.java:196-213`.
3. **P1 — `ExplicitInitialization` silently changes program behaviour** when a superclass
   constructor calls an overridable method (verified by running before/after: prints `0` before,
   `5` after). `ExplicitInitialization.java:44-48`.

Also material: several lint rules silently delete the user's comments (and the round-trip verifier
structurally cannot catch it), a single pathological file aborts an entire batch run, and
`docs/rules.md` — the user-facing contract — does not match the implemented rule catalogue in four
places.

Encouraging: 170 JDK source files (`java/io`, `java/nio/file`, `java/util/function`,
`java/util/regex`) formatted with zero internal errors, and the parse-error contract (return input
unchanged + `Diagnostic.ParseError`) is implemented and tested.

## Findings

### [P0] `RedundantModifier` spins forever on a comment inside a modifier list

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/RedundantModifier.java:96-120`
  (specifically the non-advancing branch at `:117-119`)
- **What**: `removeModifier` scans the source forward from the `ModifiersTree` start looking for the
  keyword to delete. The loop advances past whitespace, annotations (`@`) and identifier characters.
  Any other character — most importantly `/` from a comment — matches none of those branches, so
  `pos` never advances and the outer `while (pos < src.length())` loop never terminates. Confirmed
  with `jstack` on a hung process:

  ```
  at io.javafmt.lint.RedundantModifier.removeModifier(RedundantModifier.java:120)
  at io.javafmt.lint.RedundantModifier.checkInterfaceMethodModifiers(RedundantModifier.java:74)
  ```

  Real-world triggers, both from JDK 25 `src.zip`:
  - `java.base/java/util/stream/Stream.java:1474-1476` —
    `@SuppressWarnings("varargs") // Creating a stream from an array is safe` then
    `public static<T> Stream<T> of(T... values)`.
  - `java.base/java/lang/invoke/BoundMethodHandle.java:236` —
    `private  // make it private to force users to access the enclosing class first`.

  Minimal repro (hangs indefinitely; control without the comment finishes in 0.13 s):

  ```java
  interface Inf {

      public /* c */ abstract void m();
  }
  ```

  Running `javafmt --check` over 1028 `java.base` files never terminated and produced **no output
  at all** — the batch pipeline buffers, so the user cannot even tell which file is stuck.
  `ModifierOrder.java:79-82` has the same scan shape but correctly `break`s when nothing matches,
  which is why it is unaffected.
- **Why it matters for release**: an unkillable hang on the single most common annotation idiom in
  Java (`@SuppressWarnings("…") // why`). A CI job or a Gradle daemon running javafmt pins a core
  and never finishes. This is the worst possible first impression.
- **Suggested fix**: in the `else` branch, when neither a keyword nor an identifier character
  matches, either `break` (like `ModifierOrder`) or skip the comment explicitly; add a
  `pos` progress assertion. Add corpus cases with comments in every modifier position.
- **Effort**: S
- **Confidence**: confirmed (jstack + two JDK files + minimal repro)

### [P0] `LocalVarUseVar` produces non-compiling output for same-named static factories

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/LocalVarUseVar.java:176-213`
- **What**: `tryStaticFactoryRewrite` rewrites `Type<A> x = Type.factory(...)` to
  `var x = Type.<A>factory(...)`, lifting the *declared type's* type arguments onto the call as an
  explicit type witness. It matches on nothing but the receiver's simple name equalling the raw
  declared type name; it never checks that the factory method has the same number of type
  parameters. When the counts differ, javac rejects the witness. Verified end to end:

  ```
  in:  Comparator<String> c = Comparator.comparing(String::length);
  out: final var c = Comparator.<String>comparing(String::length);
  javac: error: no suitable method found for comparing(String::length)
           (wrong number of type arguments; required 2)
  ```

  javafmt exits 0 with no diagnostic. The lint engine's re-parse only checks *syntax*, and
  `RoundTripVerifier` compares tokens, so nothing catches it. `Comparator.comparing` is one of the
  most common static factories in modern Java.
- **Why it matters for release**: javafmt's headline promise is "the formatter never breaks code"
  (`docs/rules.md § Lint pass`). This breaks the build on ordinary source, silently.
- **Suggested fix**: require attribution to perform the witness rewrite (compare the resolved
  method's type-parameter count, or simply re-attribute and check the inferred type), or restrict
  the parse-only path to the case where the call already carries an explicit witness
  (`LocalVarUseVar.java:190-195`, which is already safe). Add `Comparator.comparing` to
  `LintCompileSafetyTest`.
- **Effort**: M
- **Confidence**: confirmed (formatted then compiled)

### [P1] `ExplicitInitialization` can change program behaviour

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/ExplicitInitialization.java:44-48`
- **What**: the rule strips `= 0` / `= null` / `= false` from any non-`final` field of a class or
  enum. Instance field initializers run *after* the superclass constructor, so when a superclass
  constructor calls an overridable method that assigns the field, the explicit default is a
  meaningful write. Removing it changes the observed value. Verified by compiling and running both
  versions:

  ```java
  class Super { Super() { init(); } void init() {} }
  class Sub extends Super {
      private int x = 0;               // javafmt rewrites to: private int x;
      @Override void init() { x = 5; }
      int x() { return x; }
  }
  // new Sub().x()  ->  before: 0   after: 5
  ```

  Checkstyle's `ExplicitInitialization` is detection-only, so it never had to be safe here.
- **Why it matters for release**: an unconditional auto-fix that silently changes runtime behaviour
  violates the rule contract in `LintRule`'s own javadoc ("should never produce an edit that breaks
  compilation **or changes program behavior**").
- **Suggested fix**: skip the rewrite when the enclosing class has an `extends` clause (cheap and
  parse-only), or gate the rule behind `--analyze` and skip when any supertype constructor is
  reachable. At minimum document the caveat.
- **Effort**: S
- **Confidence**: confirmed (executed before/after)

### [P1] Lint rules silently delete the user's comments

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/RedundantSuperCall.java:66-72`;
  `javafmt-core/src/main/java/io/javafmt/lint/MultipleVariableDeclarations.java:100-108`;
  enabling condition at `javafmt-core/src/main/java/io/javafmt/Javafmt.java:282-288`
- **What**: both rules replace a *source span* and rebuild its text, so anything in the span that
  the rebuild does not reproduce is lost. Verified:

  ```
  in:   Cm() {  super(); // keep this note
        }
  out:  Cm() {}                                  <- comment gone

  in:   int a = 1, /* keep me */ b = 2;
  out:  final int a = 1;
        final int b = 2;                         <- comment gone
  ```

  `RedundantSuperCall.deleteEdit` deletes the whole line when nothing precedes the statement, which
  takes any trailing comment with it. `MultipleVariableDeclarations.buildEdit` reconstructs the
  group from modifiers + type + names + initializers only.

  This is structural, not incidental: `Javafmt.verifyOutput` compares the printed output against the
  **post-lint** source, so any comment a lint edit deleted is already absent from both sides. No
  check exists on the pre-lint → post-lint transition for comment preservation.
- **Why it matters for release**: `docs/rules.md § Javadoc and comments` promises "Preserved
  verbatim. Every `//`, `/* */`, and `/** */` lands in the output with its original text intact."
  Silently discarding a developer's comment is data loss with no diagnostic.
- **Suggested fix**: have span-replacing rules re-emit comments found inside the span (or refuse the
  edit when the span contains one — `CommentScanner` already gives the positions). Independently,
  add a pre-lint→output comment-multiset check so the class of bug cannot recur.
- **Effort**: M
- **Confidence**: confirmed

### [P1] `ExplicitInitialization` reports a bogus parse error in the user's code

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/ExplicitInitialization.java:85-104`
  (backward `=` scan at `:93-96`)
- **What**: `buildDeleteEdit` scans backwards from the initializer for `'='`, bounded only by the
  declaration start. A comment between `=` and the literal makes the scan latch onto an `=` inside
  the comment, so the deletion starts mid-comment and leaves it unterminated. The lint engine's
  re-parse then fails and the failure is classified as a `ParseException`, which
  `Javafmt.failureToResult` (`Javafmt.java:295-297`) maps to `Diagnostic.ParseError` — "the input is
  at fault". Verified:

  ```
  in:  private int x = /* default = zero */ 0;
  out: <stdin>: error: ParseException: unclosed comment; reached end of file while parsing   (exit 1)
  ```

  The file is valid Java. It is returned unchanged (no corruption), but `--check` fails the build
  and the message blames the user.
- **Why it matters for release**: a red CI run with a fabricated syntax error on correct code is the
  kind of bug that gets a formatter uninstalled.
- **Suggested fix**: find the `=` via the token/comment scanners rather than a raw backward
  character scan, and bail out when the candidate offset falls inside a comment. Separately, a lint
  rewrite that fails to re-parse should surface as `Diagnostic.InternalError`, never `ParseError` —
  `LintEngine.FileState.accept` (`LintEngine.java:185-189`) should wrap the failure so the two
  cannot be confused.
- **Effort**: S (both parts)
- **Confidence**: confirmed

### [P1] One pathological file aborts the entire batch run

- **Where**: `javafmt-core/src/main/java/io/javafmt/BatchFormatter.java:141-163` (`resultFor` catches
  `RuntimeException | AssertionError` only; `await` rethrows),
  `BatchFormatter.java:91` (`lintBatch` calls `LINT_ENGINE.lintAll` with no guard),
  `BatchFormatter.java:169` (`JavaParser.parseUnits` with no guard);
  contract at `javafmt-core/src/main/java/io/javafmt/Javafmt.java:28-32`
- **What**: `Javafmt`'s class javadoc states "Formatting never throws … on failure, hands back the
  original source unchanged". The batch path does not honour that. A deeply nested expression
  (3000-term `1 + 1 + …`, the shape generated code produces) overflows the stack in a render thread;
  `StackOverflowError` is an `Error`, so neither `resultFor`'s catch nor `Javafmt.render`'s catch
  (`Javafmt.java:265`) sees it, and `await` converts it to
  `IllegalStateException("formatting failed")` that escapes `Javafmt.formatAll`. Verified: a
  directory containing one good file and one such file produced no result for either, only a stack
  trace. The same hole exists upstream — an exception from any lint rule or from
  `JavaParser.parseUnits` propagates out of `formatAll` and kills the whole run, even though the
  single-file path (`Javafmt.formatWithContext`) guards correctly.
- **Why it matters for release**: the per-file-diagnostic design exists precisely so one bad file
  cannot abort a run; the batch path — the one every integration uses — does not deliver it.
- **Suggested fix**: catch `StackOverflowError` (or `Throwable` minus the unrecoverable ones) in
  `resultFor` and `Javafmt.render`; make `await` return a `FormatResult` carrying a
  `Diagnostic.InternalError` instead of throwing; wrap `lintBatch` and `parseOnlyContexts` per chunk
  so a failure degrades to per-file failures.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `docs/rules.md` does not match the implemented lint catalogue

- **Where**: `docs/rules.md § Lint pass` vs `javafmt-core/src/main/java/io/javafmt/lint/LintRules.java:9-43`
- **What**: four mismatches in the document users are pointed at as "the contract":
  - **Missing rules.** `RedundantSuperCall` (in `CLAUDE.md`, absent from `docs/rules.md`),
    `ExplodeStarImports` (`LintRules.java:20`) and `RedundantTypeArguments` (`LintRules.java:16`,
    339 lines) appear in neither doc's auto-fix table. `RedundantTypeArguments` diamond-ifies
    constructors and drops type witnesses — a visible rewrite users should be told about.
  - **Contradiction on wildcards.** `docs/rules.md § Imports` says "If `import foo.*` appears, the
    formatter leaves it but emits a warning", but `ExplodeStarImports` rewrites it under
    `--analyze`.
  - **`RedundantModifier` described wrongly.** Both `docs/rules.md` and `CLAUDE.md` say it strips
    "`final` on private methods". The implementation
    (`RedundantModifier.java:54-70`) strips `final` on methods of a **final class** — a different
    rule. The class javadoc at `RedundantModifier.java:15-24` is the accurate one.
  - **`static` on nested records.** Both docs claim it is stripped; `checkNestedTypeModifiers`
    (`RedundantModifier.java:44-52`) handles only `INTERFACE` and `ENUM`.
- **Why it matters for release**: `docs/rules.md` opens with "The rules below are the contract".
  Three undocumented code rewrites and one wrong description undermine that on day one.
- **Suggested fix**: regenerate the table from `LintRules.DEFAULTS` (or add a test asserting the
  documented list matches the registry), and fix the `RedundantModifier` and wildcard entries.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Public API surface is far larger than intended, with no module boundary

- **Where**: `javafmt-core/src/main/java/io/javafmt/parser/` (13 public types),
  `javafmt-core/src/main/java/io/javafmt/lint/` (33 public types),
  `javafmt-core/src/main/java/io/javafmt/JavaModifiers.java:18`; no `module-info.java` anywhere in
  the repo; jar manifest carries no `Automatic-Module-Name`
- **What**: every lint rule, `JavaParser`, `ParsedUnit`, `CommentAttacher`, `TreeFacts`,
  `RoundTripVerifier` and friends are `public` purely so other packages inside `javafmt-core` can
  reach them. I checked what the integration modules actually import: only `io.javafmt.{Javafmt,
  JavafmtConfig, FormatResult, Diagnostic, Position, PathFilter, SourceFiles, JavafmtVersion}` and
  `io.javafmt.cache.{FormatCache, FileFormatCache}`. Nothing outside core touches
  `io.javafmt.parser` or `io.javafmt.lint` — 46 types that become a compatibility commitment the
  moment the jar hits Maven Central. `JavaModifiers` is likewise public but used only inside core.
  With no `module-info.java` and no `Automatic-Module-Name`, JPMS consumers also get a module name
  derived from the jar filename, which changes if the artifact is ever renamed.
- **Why it matters for release**: v1 freezes this. Every future refactor of the lint or parser
  packages becomes a breaking change, or the project has to publicly disclaim its own packages.
- **Suggested fix**: add `javafmt-core/src/main/java/module-info.java` exporting only `io.javafmt`
  and `io.javafmt.cache`; set `Automatic-Module-Name: io.javafmt.core` in the jar manifest for
  classpath consumers. Failing that, state in `package-info.java` for `parser`/`lint` that they are
  internal and unsupported.
- **Effort**: M (module-info needs `requires jdk.compiler` and a check that Lombok/errorprone are
  happy); S for the manifest attribute alone
- **Confidence**: confirmed

### [P1] One unreadable directory silently aborts the whole walk

- **Where**: `javafmt-core/src/main/java/io/javafmt/SourceFiles.java:20-27`
- **What**: `Files.walk` reports I/O failures encountered *during traversal* as
  `UncheckedIOException` thrown from the terminal operation, which the `catch (IOException e)` at
  `:25` does not catch. One unreadable subdirectory therefore aborts discovery for the entire tree —
  no partial results. Verified end to end: a tree with one `chmod 000` subdirectory produced
  `javafmt: .: cannot walk directory: java.nio.file.AccessDeniedException: ./secret` and formatted
  nothing — and (CLI side, cross-boundary) exited **0** in `--check` mode, so CI goes green having
  checked nothing.
- **Why it matters for release**: a silent CI false-pass is worse than a failure.
- **Suggested fix**: use `Files.walkFileTree` with a `visitFileFailed` that records and continues,
  or catch `UncheckedIOException` and return what was collected plus a diagnostic. The CLI should
  exit non-zero when discovery failed.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `MissingSwitchDefault` fires on exhaustive pattern switches

- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/MissingSwitchDefault.java:8-11, 26-33`
- **What**: the javadoc claims "Arrow-form switches (which are always expressions in Java 14+) are
  also excluded" — that is wrong; arrow-form switch *statements* exist and are matched by
  `visitSwitch`. The rule warns on an exhaustive arrow-form pattern switch over a sealed type, where
  adding `default` would actively hurt (it disables the compiler's exhaustiveness checking).
  Verified:

  ```
  switch (s) { case Circle c -> …; case Square q -> …; }
  -> warning: switch statement does not have a 'default' case
  ```
- **Why it matters for release**: javafmt markets itself as "for modern LTS Java". Warning on the
  canonical modern-Java construct, on every such switch in a codebase, is noise users cannot
  suppress (there are no opt-out directives by design).
- **Suggested fix**: skip switches whose cases are all patterns, or whose selector is a sealed type /
  enum covered exhaustively (parse-only heuristic: any `case <Type> x ->` label). Fix the javadoc.
- **Effort**: S
- **Confidence**: confirmed

### [P1] (cross-boundary, builder) Multi-variable `for` init makes javafmt refuse the file

- **Where**: surfaces at `javafmt-core/src/main/java/io/javafmt/Javafmt.java:258-261`; the defect is
  in the `for`-statement renderer (other reviewer's scope)
- **What**: `for (int i = 0, j = 1; i < j; i++)` renders with a duplicated `int`, which
  `RoundTripVerifier` catches:
  `error: javafmt changed more than layout and refused to write the result: tokens changed: output
  dropped nothing and added 'int' (40 tokens in, 41 out)`. The file is left unformatted and
  `--check` exits 1. One line on the lint side: `MultipleVariableDeclarations` correctly leaves
  `for`-init groups alone, so this is purely the renderer.
- **Why it matters for release**: a textbook two-variable loop makes javafmt fail the file.
- **Suggested fix**: hand to the builder/printer reviewer; add a fixture.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Thread-safety of the public entry points is not documented

- **Where**: `javafmt-core/src/main/java/io/javafmt/Javafmt.java:23-32`
- **What**: `JavaParser` documents its threading model carefully (`JavaParser.java:33-45`), but
  `Javafmt` — the type users actually touch — says nothing. As far as I can tell it *is* safe:
  `LINT_ENGINE` (`Javafmt.java:38`) holds an immutable rule list, rules keep no state across
  `apply`, and file managers are `ThreadLocal`. There is no test asserting it.
- **Why it matters for release**: Gradle/Maven/IDE hosts call this from pools; users need the
  guarantee in writing.
- **Suggested fix**: state "safe for concurrent use from multiple threads" on `Javafmt`, and add a
  concurrency test formatting the same corpus from N threads and asserting identical output.
- **Effort**: S
- **Confidence**: likely (reasoned from the code; not stress-tested)

### [P2] Cache key ignores everything except the formatter version

- **Where**: `javafmt-core/src/main/java/io/javafmt/cache/FileFormatCache.java:43`
- **What**: the header is `"# javafmt cache v2 " + JavafmtVersion.VERSION`. It does not include
  whether the run used `--analyze`, which changes the output (`ExplodeStarImports`,
  `RedundantTypeArguments`, the `LocalVarUseVar` analyze path all fire only under it). A plain run
  followed by an `--analyze` run skips every file the first run recorded, so the analyze-only
  rewrites never happen. `JavafmtConfig.lint()` has the same issue. Also: `save()` writes to a
  fixed `<name>.tmp` (`:135`), so two concurrent javafmt processes on the same cache file can
  interleave writes into that temp before the atomic move (recoverable — `load` discards a malformed
  file — but the run's work is lost).
- **Why it matters for release**: stale skips are a correctness surprise that looks like "javafmt
  didn't do anything".
- **Suggested fix**: fold a hash of the effective config (analyze flag, classpath, `lint`,
  `verifyOutput`) into `HEADER`; give the temp file a unique suffix.
- **Effort**: S
- **Confidence**: confirmed (by reading; not reproduced end to end)

### [P2] BOM stripped and CRLF converted, both undocumented

- **Where**: `javafmt-core/src/main/java/io/javafmt/parser/JavaParser.java:385-387` (BOM);
  `javafmt-core/src/main/java/io/javafmt/builder/CommentDocs.java:18` (`\r` stripping)
- **What**: a UTF-8 BOM is removed from the output, and a CRLF file is rewritten entirely to LF.
  Both are verified (`od -c` on the output). The CRLF behaviour is tested
  (`JavafmtTest.crlfInput_producesLfOnlyOutput`) but neither is mentioned in `docs/rules.md`.
- **Why it matters for release**: on Windows/`core.autocrlf` repositories the first javafmt run
  produces a whole-file diff on every file, with no documentation to point at.
- **Suggested fix**: add a "Line endings and encoding" section to `docs/rules.md`: input is UTF-8,
  output is LF-only, a BOM is removed.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Parse-failure behaviour is settled in code but still an open question in the docs

- **Where**: `ROADMAP.md` open question 4; implemented at
  `javafmt-core/src/main/java/io/javafmt/Javafmt.java:299-306` and
  `javafmt-core/src/main/java/io/javafmt/FormatResult.java:10-14`; tested in
  `javafmt-core/src/test/java/io/javafmt/FormatResultTest.java:23-42`
- **What**: the behaviour ("return input unchanged plus diagnostics, exit 1") is implemented,
  correct and tested — I verified it on malformed input — but `ROADMAP.md` still lists it as
  undecided and no user-facing doc states it. Neither `docs/intro.md` nor `docs/rules.md` tells a
  user what happens to a file that does not parse.
- **Why it matters for release**: shipping a roadmap that calls a settled contract "open" reads as
  unfinished.
- **Suggested fix**: close the open question, and add one paragraph to `docs/rules.md`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Dead / misplaced public API

- **Where**: `javafmt-core/src/main/java/io/javafmt/parser/JavaParser.java:68`
  (`HAS_JAVAC_INTERNAL_ACCESS`); `javafmt-core/src/main/java/io/javafmt/JavaModifiers.java:18`
- **What**: `HAS_JAVAC_INTERNAL_ACCESS` is a public field whose only reference in the whole repo is
  a test asserting it is `false` (`JavaParserTest.java:126`); its javadoc describes "a future opt-in
  fast path". `JavaModifiers` is public in the root package but used only by `io.javafmt.builder`
  and `io.javafmt.lint`.
- **Why it matters for release**: speculative public API on a surface about to be frozen.
- **Suggested fix**: delete `HAS_JAVAC_INTERNAL_ACCESS` (and its probe) until the fast path exists;
  move `JavaModifiers` into an internal package or make it package-private with the callers.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Smaller correctness and hygiene items

- **Where**: as listed
- **What**:
  - `RemoveUnusedImports.spanOf` (`RemoveUnusedImports.java:119`) finds the import's terminator with
    `source.indexOf(';', start)`, which lands inside a comment for
    `import java.util.List /* ; */;`. Same textual-scan hazard as the
    `ExplicitInitialization` finding above. (unverified — reasoned from the code)
  - `ParseException.diagnostics` (`ParseException.java:14`) is `transient` in a `@NullMarked`
    package, so a deserialized instance has a `null` field the API declares non-null.
  - `ArrayTrailingComma` (`ArrayTrailingComma.java:41-57`) adds the comma unconditionally, producing
    `final int[] c = {1,};` on single-line arrays. Checkstyle's check exempts arrays whose braces
    share the element's line. Matches the written docs, so a style call rather than a bug — but
    worth deciding deliberately before v1 freezes it.
  - `ModifierOrder` (`ModifierOrder.java:60-62`) silently skips every declaration carrying an
    annotation, so `@Getter static public int x;` is never reordered. Undocumented limitation.
  - No default path exclusions anywhere (`PathFilter`/`SourceFiles` and all three integrations), so
    `javafmt .` formats `build/generated/**` and `target/generated-sources/**`. Users will hit this
    on their first run.
  - `docs/intro.md` documents the CLI, Maven, Gradle, VS Code and IntelliJ entry points but never
    the Java API, although `javafmt-core` is itself a published artifact. No `Javafmt.format(...)`
    example exists in the docs.
- **Why it matters for release**: individually small; collectively the difference between "polished"
  and "early".
- **Suggested fix**: as described per item.
- **Effort**: S each
- **Confidence**: confirmed except where noted

## Coverage notes

**Read fully**: `Javafmt.java`, `BatchFormatter.java`, `JavafmtConfig.java`, `FormatResult.java`,
`Diagnostic.java`, `Position.java`, `PathFilter.java`, `SourceFiles.java`, `JavaModifiers.java`,
`package-info.java`; `parser/JavaParser.java`, `ParsedUnit.java`, `ParseException.java`,
`ParseOutcome.java`, `AttributedOutcome.java`, `AttributedUnit.java`, `Attribution.java`,
`RoundTripVerifier.java`, `TreeFacts.java`; `lint/LintEngine.java`, `LintRules.java`,
`LintRule.java`, `LintResult.java`, `LintEdit.java`, `LintContext.java`, `LintScan.java`,
`Modifiers.java`, `ReassignmentDetector.java`, and every auto-fix rule
(`FinalLocalVariable`, `FinalParameters`, `ExplicitInitialization`, `LocalVarUseVar`,
`MultipleVariableDeclarations`, `RedundantModifier`, `ModifierOrder`, `ArrayTrailingComma`,
`ArrayTypeStyle`, `NeedBraces`, `RedundantSuperCall`, `OneStatementPerLine`, `EmptyStatement`,
`UpperEll`, `NewlineAtEndOfFile`, `RemoveUnusedImports`, `ExplodeStarImports`); all three `cache/`
classes; `javafmt-core/build.gradle.kts`; `CLAUDE.md`, `ARCHITECTURE.md`, `ROADMAP.md`,
`docs/rules.md`, `docs/intro.md`.

**Skimmed**: `RedundantTypeArguments.java` (first 120 of 339 lines — enough to confirm it is an
undocumented rewriting rule; its analyze-path safety argument is *not* audited), the warning-only
rules (`FallThrough`, `StatementFlow`, `AvoidStarImport`, `EqualsHashCode`, `EmptyBlock`,
`HideUtilityClassConstructor`, `CovariantEquals`, `StringLiteralEquality`) — headers and predicates
only, `DefaultComesLast` (behaviour verified empirically instead of line by line), and the test tree
(`LintCompileSafetyTest`, `FormatResultTest`, `JavafmtTest` method names, the oracle suite listing).

**Not covered**: `parser/CommentAttacher.java` (417 lines), `CommentScanner.java`,
`TokenScanner.java`, `CommentToken.java`, `SourceToken.java` — comment attachment is closest to the
other reviewer's fixture/printer scope; `doc/`, `printer/`, `builder/` by assignment. No test was
executed (`gradle` was off limits); all empirical results come from the checked-in
`javafmt-cli-0.1.0-…-all.jar`, which I verified is newer than every `javafmt-core` source file, so
it reflects `2f40ba7`. Corpus coverage was JDK 25 `java.base`: `java/io` (92), `java/nio/file` (78),
`java/util/function` (44), `java/util/regex` (9) all clean; `java/lang` (489) and `java/util` (369)
could not be completed because of the `RedundantModifier` hang.

**Housekeeping**: two orphaned `java` processes from the hang repro (PIDs 42312 and 42526) are still
spinning at ~100% CPU — the sandbox refused my `kill -9`. They can be killed safely with
`kill -9 42312 42526`.
