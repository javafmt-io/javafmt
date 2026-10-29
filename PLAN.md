# javafmt — making the formatter trustworthy

> **For agentic workers:** read `prompt.md` first; it says how to execute this plan (TDD, commits,
> verification, when to ask). Items use `- [ ]` checkboxes; tick them and append a one-line
> **Fixed:** note with the commit hash and the evidence.

**Goal:** be able to run javafmt over other real projects and trust the result without reading the
diff line by line. Publishing, marketplaces, docs and CI polish are out of scope for now; that
backlog is parked, unchanged, in `review/release-backlog.md`.

**Basis:** seven read-only reviews at HEAD `2f40ba7` (2026-09-19), each reproducing its findings
against the built formatter and, for core, against the JDK 25 `java.base` sources. Full reports with
inputs and observed outputs are in `review/`; the plan entries below are summaries and cite the
report to read first. `review/` and this plan are working notes, not for publication.

---

## What "trustworthy" means, and how each property gets proven

| Property | Today | Gate that proves it |
|---|---|---|
| **T1 Never changes program meaning.** Output compiles and behaves identically. | Two lint rules break compilation or change behavior; unary `- -x` becomes `--x`; several renderers drop tokens, hidden only by the verifier. | Compile-safety tests per rule; corpus gate (C1); build-and-test of a real project after formatting (C2). |
| **T2 Never loses or relocates a comment.** | Two lint rules delete comments silently; the verifier cannot see it; four fixtures encode comment relocation as expected output. | Pre-lint to output comment check inside `verifyOutput` (A4); corpus gate. |
| **T3 Never hangs or crashes.** | `RedundantModifier` loops forever on `@SuppressWarnings("x") // why`; a `StackOverflowError` aborts a whole batch with a stack trace. | Per-file time budget and zero internal errors in the corpus gate. |
| **T4 Formats all of Java 25, does not refuse ordinary files.** | Every generic type, every multi-variable `for`, every wrapping enum, stray `;` members and class-tail comments are refused as `InternalError`. 12% of `java.base` hit this. | Zero `InternalError` diagnostics across `java.base` (C1). |
| **T5 Idempotent and stable.** `format(format(x)) == x`, and the output policy is settled so later fixes do not reformat every project again. | Idempotency is tested on fixtures only; eight constructs ignore the 150-char width; blank-line and comment policy disagree with the docs. | Idempotency over the corpus (C1); decisions D2 to D11 made and pinned by fixtures (Tier D). |
| **T6 Safe to run from the CLI or Spotless.** | In-place writes are truncate-then-write; one bad file aborts the run; `javafmt .` rewrites `build/`; `--check` exits 0 on an unreadable directory. | `CliTest` cases (Tier E); the dogfood procedure (C2). |
| **T7 The IntelliJ and Gradle plugins do what they claim.** | IntelliJ: any warning-only lint hit discards the format and blames the file; the version gate rejects every 0.1.x binary including a local build; the format runs on the EDT under a write action; Homebrew/SDKMAN binaries are not found. Spotless: the documented wiring defeats up-to-date checking; warnings are invisible; no test runs real Spotless. | Plugin tests through the platform's own entry point and a Gradle TestKit run (Tier F); format-on-save in a real IDE session as part of C2. |

---

## Decisions that affect output (make them now or reformat everything later)

Every one of these changes bytes in formatted files. Deciding after javafmt is in use on other
projects means a second whole-repo diff. Recommendations are what the plan assumes.

| # | Question | Recommendation |
|---|---|---|
| D2 | **Line endings.** Every path rewrites CRLF to LF and strips a BOM. | **Decided 2026-09-20: optimise for code-review diffs.** Preserve the input file's dominant line ending and its BOM on output, so a format never produces a whole-file line-ending diff. A file with mixed endings is normalised to its dominant one. Implemented by D-6. |
| D3 | **Enum trailing comma.** Promised; emitted only in the shape the verifier rejects; never with body members. | **Decided 2026-09-20: keep the rule.** Implement as a lint edit so the verifier baseline already has it (B5). |
| D4 | **Blank line between every member, or only at visibility changes.** Code: every member. Docs: visibility changes. | **Decided 2026-09-20: do what google-java-format does.** Methods, constructors, nested types and initializer blocks always get exactly one blank line before and after. Between consecutive fields the author's grouping is preserved: zero or one blank line, runs collapsed to one. A field that carries Javadoc is treated like a method (blank line on both sides). javafmt's own "no blank line directly after `{` or before `}`" rule still applies. The executor verifies this description against `JavaInputAstVisitor.addBodyDeclarations` in google-java-format before implementing. Implemented by D-2. |
| D5 | **Blank lines inside method bodies.** Code deletes all; docs say collapse runs to one. | **Decided 2026-09-20: follow the docs.** Collapse runs to one blank line, keep single blank lines. Implemented by D-2. |
| D6 | **One-line braced `if`.** Braces are always required and `NeedBraces` auto-fixes them; that is not in question. The question is whether an already-braced `if (c) {s;}` that fits in 150 chars may stay on one line (the docs say yes) or is always expanded to three lines (what the code does). | **Decided 2026-09-19: preserve.** If the author wrote `if (cond) {foo();}` or `if (cond) {foo();} else {bar();}` on one line, in the compact form with no spaces inside the braces, leave it exactly as written. Anything not in that shape is expanded as today. Implemented by D-3. |
| D8 | **Comment relocation** (`else /* c */ if`, trailing comments on arguments and in chains). Baked into four fixtures. | **Decided 2026-09-20: fix it** (D-4, effort L), and rewrite the four fixtures that encode the relocation. |
| D9 | **`ArrayTrailingComma` on one-line arrays** produces `{1,}`. | **Decided 2026-09-20: trailing comma only when the initializer spans multiple lines.** One-line `{1, 2, 3}` stays as written; a multi-line initializer gets the comma after its last element. Same principle already holds for enums under D3: the comma is emitted only when the constant list breaks. Implemented by D-5. |
| D10 | **`module-info` `provides ... with` sorting** can change `ServiceLoader` order. | **Decided 2026-09-20: do what google-java-format does.** google-java-format never reorders anything in a module declaration: directives, `exports ... to` targets and `provides ... with` implementations all stay in source order. javafmt drops all sorting in `ModuleRenderer`. **Corrected 2026-09-20 against `JavaInputAstVisitor.visitModule`:** the plan said blank lines should follow the author. They do not — google-java-format emits `BlankLineWanted.YES` exactly when a directive's kind differs from the previous one's and `NO` otherwise, which is what javafmt's `ModuleRenderer.bodyStream` already does, so the directive-kind grouping stays. Implemented by A7. |
| D11 | **Member ordering.** General reordering was removed in `59ec035`; the only reorder left is the utility-class constructor pin. The owner wants one more: nested types at the bottom. | **Decided 2026-09-20.** Two reorders, nothing else: (1) nested types (`class`, `interface`, `enum`, `record`, `@interface`) move to the bottom of the enclosing body, keeping their relative order; (2) in a utility class, the private no-arg constructor goes to the very bottom, after the nested types, because it is useless boilerplate. Every other member stays where the author put it. Implemented by D-7. |

**All decisions are made.** Work every item without asking about policy; ask only for the reasons
listed in `prompt.md`.

Release-only decisions (parse-error policy per integration, first version number, IntelliJ fallback
text) are in `review/release-backlog.md`.

---

## Order of work

1. **B1** (the hang) and **B2** (batch isolation): until these land the corpus cannot be run.
2. **C1** (corpus gate): from here on every fix is measured against `java.base`.
3. **Tier A** (semantic safety), then the rest of **Tier B**.
4. **Tier D** decisions and fixtures, so the output stops moving.
5. **Tier E** (safe operation) and **Tier F** (IntelliJ and Spotless), then **C2** (dogfood on a
   real project, from the IDE and from Gradle) as the final gate.

Tier A, Tier B and Tier F are independent of each other and can be worked in parallel once C1 exists.

---

## Tier A — Semantic safety (T1, T2)

Harness: `gradle :javafmt-core:test` (fixtures, idempotency, differential compile) and
`gradle :javafmt-core:check` (adds the Checkstyle oracle suite). Read `review/core-api-lint.md` for
A1, A2, A4, A6; `review/core-engine.md` for A3, A5, A7.

- [x] **A1 `LocalVarUseVar` emits type witnesses that do not compile** (M)
  `javafmt-core/src/main/java/io/javafmt/lint/LocalVarUseVar.java:176-213`. `Comparator<String> c =
  Comparator.comparing(String::length)` becomes `var c = Comparator.<String>comparing(...)`; javac:
  wrong number of type arguments. Exit 0, no diagnostic. The rule lifts the declared type's arguments
  onto any same-named static factory without checking the method's type-parameter count. Fix:
  restrict the parse-only path to calls that already carry an explicit witness (`:190-195`); do the
  lifting rewrite only under attribution after comparing arity with the resolved method. Tests in
  `LintCompileSafetyTest`: `Comparator.comparing`, `Collectors.toMap`, `Map.entry`, `Optional.empty`,
  `List.of`, and a same-arity positive case that must still rewrite.
  **Fixed:** `5a81bd5` — `LintCompileSafetyTest` case `factory-witness-arity-two` failed with
  `no suitable method found for comparing(String::length) … (wrong number of type arguments;
  required 2)` and now compiles; the lift moved to the attributed path behind an
  `ExecutableElement.getTypeParameters().size()` check. Corpus refused count 291 → 283.
  **Output change:** without `--analyze`, `Optional<Path> x = Optional.empty()` now keeps its
  declared type instead of becoming `var x = Optional.<Path>empty()`; `docs/rules.md` updated in
  the same commit.

- [x] **A2 `ExplicitInitialization` changes behavior and fabricates parse errors** (S)
  `lint/ExplicitInitialization.java:44-48,85-104`. (a) Stripping `= 0` from a subclass field changes
  the value observed when a superclass constructor calls an overridable method (verified: prints 0
  before, 5 after). Skip the rewrite when the enclosing class has an `extends` clause. (b) The
  backward `=` scan latches onto `=` inside a comment (`int x = /* default = zero */ 0;`) and the
  resulting unterminated comment is reported as the user's `ParseError`. Find `=` via the token
  scanner and bail if it lands in a comment. (c) In `lint/LintEngine.java:185-189`, a lint rewrite
  that fails to re-parse is an `InternalError`, never a `ParseError`. Tests for all three.
  **Fixed:** `b307f33` — three tests that each failed at HEAD now pass:
  `fieldOfAClassWithASuperclassKeepsItsExplicitDefault` (rule now skips a class with an
  `extends` clause), `equalsSignInsideACommentIsNotMistakenForTheAssignment` (the backward scan
  skips comments, and refuses when a comment falls inside the span it would delete), and
  `aRewriteThatWillNotParseIsJavafmtsFaultNotTheUsers` (a failed re-parse is now a
  `LintRewriteException`, which `Javafmt.failureToResult` reports as `InternalError` rather
  than as the user's `ParseError`). `docs/rules.md` updated in the same commit.

- [x] **A3 `- -x` renders as `--x`** (S)
  `builder/ExpressionRenderers.java:94-100`. `renderUnary` glues the operator to the operand. Emit a
  space when the operand's rendering starts with the same sign character. Tests: `- -x`, `+ +x`,
  `- --x`, `-(-x)`, `! !b` (no space needed but must round-trip). The verifier blind spot that let
  this through is A6.
  **Fixed:** `9bc8e85` — `test-fixtures/idempotent/unary-sign-sequences.java` failed with
  `- -input` → `--input`, `+ +input` → `++input` and `- --counter` → `---counter`, and now
  round-trips; `-(-input)` and `!!(input > 0)` are unchanged, since only the sign operators can
  collide.

- [x] **A4 Lint rules delete comments, and nothing can catch it** (M)
  `lint/RedundantSuperCall.java:66-72` (`super(); // keep this` deletes the whole line),
  `lint/MultipleVariableDeclarations.java:100-108` (`int a = 1, /* keep me */ b = 2;` rebuilds the
  span without the comment). Structural cause: `Javafmt.java:282-288` verifies output against the
  *post-lint* source, so anything lint removed is absent from both sides. Fix the two rules (re-emit
  or refuse when `CommentScanner` finds a comment inside the span), then add a pre-lint to output
  comment multiset check in `Javafmt.verifyOutput` so this class of bug fails loudly. Tests: both
  repros, plus a `verifyOutput` test with a stub rule that drops a comment and must produce
  `InternalError`.
  **Fixed:** `87df95d` — `RedundantSuperCall` now deletes only the statement when the line carries a
  comment (`trailingCommentOnTheSuperCallSurvives`), `MultipleVariableDeclarations` declines a
  group with a comment inside the span it rebuilds (`groupCarryingAnInteriorCommentIsLeftAlone`),
  and `Javafmt.verifyOutput` now holds comments against the user's original source, reporting
  `a lint rule dropped a comment and javafmt refused to write the result`
  (`aCommentTheLintPassDroppedBecomesAnInternalError`). The new check finds nothing on
  `java.base`, which is the point: every comment loss there was already a renderer gap.
  Corpus refused 283 → 288: five multi-variable *field* declarations with interior comments are no
  longer split, and `FieldRenderer` cannot yet render them — B4 covers that renderer and should
  bring the five back.

- [x] **A5 Renderers that drop tokens** (S each)
  All caught by the verifier today, so the file is refused rather than corrupted; with
  `verifyOutput(false)` they emit uncompilable or semantically different code.
  - Annotation element `default` values: `builder/MethodRenderer.java:106-116` never reads
    `getDefaultValue()`; `String value() default "x";` becomes required. Extend
    `test-fixtures/idempotent/annotation-type.java`.
  - `outer.new Inner()` and `new <T>Foo()`: `builder/ExpressionRenderers.java:174-188` never reads
    `getEnclosingExpression()` or `getTypeArguments()`.
  - Type-annotated varargs (`String @N ... args`) become `String @N [] args`:
    `builder/MethodRenderer.java:92-99`; unwrap `AnnotatedTypeTree` before the `ArrayTypeTree` test.
  - Class-body tail comment (`// end` before the closing `}` of a class): `builder/TypeBodyRenderer.java:19-30`
    never calls `attacher.tail(node)`; `MethodRenderer.java:113` and `BlockRenderer.java:23` do.

  **Fixed:** `12912e9` — four idempotent fixtures, each refused at HEAD, now round-trip:
  `annotation-type` (`default` values, which `appendBody` never read),
  `inner-class-creation` (`outer.new Inner(1)` and `Fixture.<T>build(seed)`),
  `annotated-varargs` (`String @Nullable ... args`, which needed `TreeFacts.isVarargs` to unwrap
  the `AnnotatedTypeTree` as well) and `class-tail-comment`. The class tail needed a slot in
  `CommentAttacher.visitClass` too — without one the class's catch-all claimed the comment and
  nothing emitted it. Corpus refused 288 → 279.

- [x] **A6 Harden the round-trip verifier** (M)
  `parser/RoundTripVerifier.java:76` compares sorted multisets, so token *reordering* is invisible;
  `parser/TokenScanner.java:19-21,74` emits multi-char operators one character at a time, so
  `- -x` vs `--x` is invisible. Compare token *sequences* (with the deliberate lint edits accounted
  for), lex operators with maximal munch, and add the comment check from A4. Then audit the other
  textual scans the reviews flagged as the same hazard class: `lint/RemoveUnusedImports.java:119`
  (`indexOf(';')` can land in a comment), `builder/EnumRenderer.java:105-107` (regex comment
  stripper that matches inside string literals; replace with `CommentScanner`). Tests: a
  `RoundTripVerifierTest` case per blind spot, each asserting the verifier now reports it.
  **Done:** `7179c94` and the A4/B5 commits.
  - *Maximal-munch lexing* — `TokenScanner` now lexes `--`, `->`, `>>>=` and the rest whole, so
    `- -x` and `--x` are different token streams and the verifier reports the change without
    needing sequence comparison at all. `>>` and `>>>` are deliberately left split, because javac
    splits them itself so nested generics close. Four `TokenScannerTest` cases and two
    `RoundTripVerifierTest` cases.
  - *The comment check* landed with A4: comments are held against the user's original source, so a
    lint rule that deletes one is reported.
  - *The textual-scan audit* is done: `EnumRenderer.stripComments` was a regex that matched
    `/*` inside a string literal and now goes through `CommentScanner.withoutComments` (B5);
    `RemoveUnusedImports:119` was checked and its `indexOf(';')` is bounded to the import
    declaration's own span, so it cannot reach a comment.

  **Deferred — comparing token *sequences*.** Implemented, measured, reverted. It works and it is
  right in principle: it caught the A9 `new T[n][]` → `new T[][n]` move immediately. But it also
  showed that the renderer performs several normalisations that are **not** lint edits and so are
  not in the source the verifier compares against:
  1. annotations are printed before keyword modifiers, so `final @SuppressWarnings("unchecked")`
     comes out as `@SuppressWarnings("unchecked") final` — **108 of 1,276 `java.base` files**;
  2. C-style array return types are normalised, so `int first()[]` comes out as `int[] first()`
     (`ArrayTypeStyle` only visits variables, never methods);
  3. the corpus went from 29 refused to 137 on these alone.
  **Since done, at the owner's go-ahead — `3150b17`.** Both normalisations are lint edits now, and the
  sequence comparison is on:
  - `ModifierOrder` hoists annotations in front of the keywords, so the source says what the
    renderer prints. Three bugs had to be fixed to make it safe, each found by the corpus gate:
    `Modifiers.skipAnnotation` stopped at the first `.` of a qualified name, so
    `@jdk.internal.RequiresIdentity` was half-deleted by the rewrite (it also now skips string
    literals, so a `)` inside `@SuppressWarnings("a)b")` no longer ends the argument list); the
    replacement span was computed by summing annotation lengths rather than from their real offsets
    (**3 files non-idempotent**); and `@interface` was treated as an annotation and hoisted in
    front of the modifiers, which does not parse (**12 annotation types refused**). A block comment
    among the modifiers is carried through the rewrite; a `//` comment still stops it.
  - `ArrayTypeStyle` now normalises a C-style return type (`int first()[]` → `int[] first()`)
    instead of leaving it to the renderer.
  - `RoundTripVerifier` compares tokens **in order**, with the import declarations still held to a
    multiset because sorting them is the one reordering the renderer still performs on its own.
  **Result: the corpus stayed at 15 refused, 0 non-idempotent** — the sequence comparison costs
  nothing and every refusal now reports *where*. `Javafmt` turns the diverging token's offset into
  a `Position`, so a refusal reads
  `ZipCoder.java:72:24: error: javafmt bug: … the output diverges at token 84, where the source has
  `,`, `DIRECTORY_MATCH`, `=`, `1`, `,` and the output has `;`, `static`, `final`, …*
  — which names the defect (a multi-variable field group) outright. The offset is into the post-lint
  source, so a heavily-linted file can report a line slightly off; a comment-only difference still
  has no position, since the token comparison cannot locate one.

- [x] **A7 `module-info` reordering** (S, D10 decided)
  `builder/ModuleRenderer.java:95-98` sorts `exports`/`opens ... to` targets and `provides ... with`
  implementations; `:42-45` inserts blank lines between directive kinds. HotSpot's `ServiceLoader`
  honours declaration order for module-declared providers, so sorting `with` can change what
  `findFirst()` returns. Per D10, match google-java-format: no reordering of directives or of any
  target list, blank lines between directives as the author wrote them (zero or one, runs
  collapsed). Remove the sorting and the kind-grouping. Fixtures: `provides S with B, A;` and
  `exports p to z, a;` stay in that order; a module with author blank lines is preserved; one with
  a triple blank line collapses to one. Confirm the description of google-java-format's behavior
  against `JavaInputAstVisitor.visitModule` first.
  **Fixed:** `9dd34b5` — `ModuleRenderer.renderTargeted` no longer sorts; `exports a.b to zeta, alpha,
  beta` and `provides a.Service with z.Z, a.A, m.M` now come out in source order
  (`exportsTo_targetsKeepSourceOrder`, `opensTo_targetsKeepSourceOrder`,
  `provides_implsKeepSourceOrder`, and three rewritten fixtures). **The plan's blank-line clause was
  wrong** and is corrected in the D10 row above: `JavaInputAstVisitor.visitModule` sets
  `BlankLineWanted.YES` exactly when the directive kind changes, so javafmt's existing kind grouping
  is already google-java-format's behaviour and was kept. `docs/rules.md` gains a
  *Module declarations* section.

- [x] **A8 Audit `RedundantTypeArguments` on anonymous classes** (M)
  `lint/RedundantTypeArguments.java` (339 lines) rewrites `new Comparator<String>() {...}` to
  `new Comparator<>() {...}`. Diamond with anonymous classes is legal only when the inferred type
  is denotable (JLS 15.9.1). Neither reviewer audited its safety argument. Read it, write
  `LintCompileSafetyTest` cases for the non-denotable inference shapes (intersection types, capture,
  wildcard-parameterised targets), and restrict or gate behind attribution as the tests dictate.
  **Audited, no restriction needed:** `6a6abd6`. The rule fires only where the target type's arguments
  are written identically to the constructor's, so the type javac infers is one the author already
  wrote down and is denotable by construction. The shapes that would infer something unnameable —
  an intersection, a captured wildcard — reach a diamond only with no target type to match, which
  means `var` or an argument position, and the rule looks at neither. Five new
  `LintCompileSafetyTest` cases hold that to account (anonymous class in a field initializer and in
  a `return`, nested generic target, type-variable target, wildcard target left alone), each run
  with and without attribution where attribution changes the path. The class javadoc now carries the
  safety argument. Two further cases (`anon-diamond-generic-superclass-with-args`,
  `anon-diamond-bounded-target`) were written and fail on **B3**, not on this rule; they land with
  B3.

- [x] **A9 Array creation moves the dimension into the wrong bracket pair** (S) — *found by C1*
  `new Annotation[numParameters][]` renders as `new Annotation[][numParameters]`, which does not
  compile. Reproduced on `java.base/java/lang/reflect/Executable.java:614`; the formatted output
  fails javac with `']' expected` at the rewritten line. The round-trip verifier accepts it because
  it compares sorted token multisets, so moving a token is invisible — the same blind spot A6
  closes, and the only file in the corpus whose output javac cannot read. Fix the array-creation
  renderer to pair each dimension expression with its own brackets; fixture covering
  `new int[n][]`, `new int[n][m]`, `new int[][] {{1}}` and `new int[n][][]`. Lower `notReparsing`
  to 0 in `corpus-baseline.properties` in the same commit.
  **Fixed:** `a6a2db8` — `test-fixtures/idempotent/array-creation-dimensions.java` failed with
  `new int[n][]` rendered as `new int[][n]` and now round-trips; the corpus gate's
  `output does not reparse` count went 1 → 0 and `notReparsing` is recorded at 0.

---

## Tier B — Never hang, crash, or refuse ordinary Java (T3, T4)

Read `review/core-api-lint.md` for B1, B2; `review/core-engine.md` for B3 to B7.

- [x] **B1 `RedundantModifier` spins forever on a comment in a modifier list** (S) — **do first**
  `lint/RedundantModifier.java:96-120`. `removeModifier` never advances `pos` on `/` (or any char
  that is not whitespace, `@`, or an identifier char). `public /* c */ abstract void m();` in an
  interface, or `@SuppressWarnings("varargs") // safe` above a method, loops at 100% CPU with no
  output; a whole-directory run never terminates. `ModifierOrder.java:79-82` has the same scan and
  correctly `break`s. Fix: skip comments via `CommentScanner`, `break` when nothing matches, assert
  progress. Tests: both repros with a JUnit timeout. Verify: the JDK's `java/util/stream/Stream.java`
  formats in under a second.
  **Fixed:** `80e6e56` — `Stream.java` now finishes in 0.287 s (`--check`, exit 1 for the unrelated
  B3 token drop) where it previously never terminated; three `RedundantModifierTest` cases with a
  5 s `SEPARATE_THREAD` timeout reproduce the hang at `RedundantModifier.java:120` and now pass.

- [x] **B2 One pathological file aborts the whole batch** (S) — **do second**
  `BatchFormatter.java:91,141-163,169`, `Javafmt.java:265`, `javafmt-cli/.../Main.java:13`. A
  2000-term `1 + 1 + ...` overflows the stack in a render thread; `resultFor` catches only
  `RuntimeException | AssertionError`, `await` rethrows `IllegalStateException`, `formatAll` dies,
  zero files are written and a stack trace is printed. `lintBatch` and `parseUnits` have the same
  hole for any rule or parser exception. Catch `StackOverflowError` alongside, wrap `lintBatch` and
  `parseOnlyContexts` per chunk, make `await` return a `FormatResult` carrying `InternalError`.
  Test: `BatchFormatterTest` with one good file and one 3000-term expression; the good file's result
  must be present and the bad file's diagnostic must be `InternalError`.
  **Fixed:** `1d1afe3` — `javafmt --check` over a directory holding one good file and a 6000-term
  expression now prints only `Deep6000.java: StackOverflowError` and exits 1, where it previously
  printed a stack trace and produced no result for either file. `JavafmtFormatAllTest` covers 3000,
  6000 and 12000 terms; all three fail without the fix (`IllegalStateException: formatting failed`
  and two escaping `StackOverflowError`s).

- [x] **B3 Type parameters dropped from class, interface and record declarations** (M)
  `builder/ClassLikeRenderer.java:20,25`, `builder/RecordRenderer.java:18,41`. `getTypeParameters()`
  is never read for type declarations, so `class Box<T>` renders as `class Box` and the file is
  refused. The single largest source of `InternalError` on `java.base`. Render them with the
  existing `MethodRenderer.renderTypeParams` group between the simple name and the
  `extends`/`implements`/`permits` header (records: before the component list), inside the same
  wrapping group as the header. Fixtures: generic class, interface, record; bounded and intersection
  bounds; a type-parameter list that must wrap; a generic sealed interface with `permits`.
  **Fixed:** `ecb7554` — `idempotent/generic-type-declarations.java` (generic class, interface,
  record, bounded and intersection bounds, generic sealed interface with `permits`) and the pair
  fixture `type-decl-type-params-long` both failed at HEAD and now pass; the wrapped shape matches
  what `type-params-long` already pins for methods. The two A8 cases that were waiting on this
  (`anon-diamond-generic-superclass-with-args`, `anon-diamond-bounded-target`) land here.
  **Corpus refused 279 → 110**, the single largest drop in the run.

- [x] **B4 Multi-variable `for` initializer repeats the type** (S)
  `builder/LoopRenderer.java:25,33-40`, `builder/FieldRenderer.java:14`. `for (int i = 0, len = n;
  ...)` renders `for (int i = 0, int len = n; ...)`. Render the first initializer in full and the
  rest, sharing the same `getType()`, as `name = init`. Fixtures: two and three variables, with and
  without initializers, with `final` and with an annotation.
  **Fixed:** `0f95a34` — `idempotent/for-shared-declaration.java` (two and three variables, one
  without an initializer, one `final`) failed at HEAD with `for (int i = 0, int len = n; …)` and
  now round-trips. The C-style `for (int i = 0, a[] = null; …)`, where the variables really do have
  different types, falls back to the old rendering and stays refused rather than losing the extra
  dimensions. **Corpus refused 110 → 57.**
  *Noted while here:* an empty update clause renders `for (a; b; )` with a space before the
  `)`. Cosmetic, no plan item covers it, left alone — filed as a bullet under D-5.

- [x] **B5 Enum trailing comma is rejected by the verifier** (M, D3)
  `builder/EnumRenderer.java:83-89`. A body-less enum that has to wrap and has no author comma comes
  back `InternalError` "added `,`". Implement the comma as a lint rule (`EnumTrailingComma`) so the
  post-lint source, which the verifier compares against, already has it; make
  `constantsBlockBeforeBodyMembers` (`:69`) consistent. Fixtures: 12-constant enum without a
  trailing comma, with and without body members, one that fits on one line (no comma added).
  **Fixed:** `b3438e2` — new `EnumTrailingComma` lint rule adds the comma when the enum will not fit
  on one line, so the verifier's baseline already has it; `EnumRenderer.trailingSeparator` no
  longer invents one. The 21-constant enum that came back
  `tokens changed: output dropped nothing and added ,` now formats clean
  (`wideEnumFormatsWithoutBeingRefused`), `enum Small { A, B }` stays on one line with no comma,
  and an author's `,` or `;` is untouched. Consistency for `constantsBlockBeforeBodyMembers` came
  out the other way round: the rule now *removes* a `,` written in front of the terminating `;`,
  since the renderer emits one separator and the pair left the output a token short.
  `EnumRenderer.stripComments` was a regex that matched `/*` inside a string literal; it now goes
  through `CommentScanner.withoutComments` (part of A6's audit list). **Corpus refused 57 → 45.**

- [x] **B6 Stray `;` class-body members are dropped** (S)
  `builder/ClassLikeRenderer.java:29` keeps only `VariableTree | MethodTree | ClassTree | BlockTree`;
  `class S { void f() {} ; }` and a `};` after a nested type are refused. Nine `java.base` files hit
  this. Extend the `EmptyStatement` lint rule to strip class-body-level `;` so the verifier baseline
  agrees. Fixture pair: input with stray semicolons, expected without.
  **Fixed:** `fd60dc0` — pair fixture `class-body-stray-semicolons` failed at HEAD with
  `tokens changed: output dropped ;, ; and added nothing` and now passes. javac's parser discards a
  class-body `;` outright, so there is no tree node to render and the semicolons had to be found in
  the source: `EmptyStatement` now also deletes any `;` between the members of a class body, with
  enums left to `EnumTrailingComma` since their `;` is a required separator. **Corpus refused
  45 → 29.**

- [x] **B7 Deep nesting is superlinear** (M)
  `doc/Doc.java:32,71` (`flatWidthOrBreak`, `canBreakInternally`) rewalk subtrees per nesting level;
  a 4,800-char nested chain takes 164 ms and the curve is roughly cubic. Memoise flat width per node
  (a bottom-up width field or an `IdentityHashMap` computed once). Not a hang at realistic depths,
  but generated code reaches them; the corpus per-file budget (C1) is the test.
  **Fixed:** `0f6b210`, **but the diagnosis in this entry was wrong.** A JFR profile of a 600-deep nest
  put 292 of 299 execution samples in `Matcher.replaceAll`, called from
  `VerbatimEmitter.stripTrailingWhitespace:138` — `replaceAll("(?m)[ \t]+$", "")`, which is
  quadratic in the length of a line because the engine restarts the greedy run of spaces at every
  position in it. Deep nesting indents to thousands of columns over hundreds of lines and the two
  multiply. Replacing it with the single scan the method already used for its other branch took the
  600-deep case from **4207 ms to 234 ms**, and flattened the curve: 201/217/234 ms at depth
  200/400/600, against 363/1424/4254 before.
  Memoising `flatWidthOrBreak` was implemented and then **reverted**: with the regex gone it made
  no measurable difference at any depth (226/222/214 ms with it, 229/198/220 ms without), so there
  was no failing test to justify the machinery. `JavafmtTest#deeplyNestedCodeFormatsInProportionToItsSize`
  pins it with a 2 s timeout; it fails at HEAD.

---

- [x] **B8 `final` dropped from a type pattern's binding variable** (S) — *found by C2*
  `builder/ExpressionRenderers.java:291` never read `getVariable().getModifiers()`, so
  `if (o instanceof final String s)` came back
  `tokens changed: output dropped final and added nothing` and the file was refused. Legal Java
  and not rare — it was the single refusal in 168 files of a real project that had never seen
  javafmt. Same shape as A5.
  **Fixed:** `b5bf3f6` — `idempotent/pattern-final-binding.java` (an `instanceof` pattern with and
  without `final`, and two `case` patterns with it) failed at HEAD and now round-trips.

---

## Tier C — Prove it (all six properties)

- [x] **C1 Corpus gate** (M) — **do third**
  Add a `corpusTest` source set in `javafmt-core` (like the existing `oracleTest`) that formats every
  file under a corpus and asserts, per file: no `InternalError` diagnostic, output re-parses,
  `format(output) == output`, no line over 150 chars (once Tier D's width items land; until then
  report the count), and a wall-clock budget of a few seconds. Corpus: the JDK 25 `src.zip` from the
  toolchain (`<javaHome>/lib/src.zip`, offline), at least `java.base/java/{util,lang,io,nio,time}`
  (about 1,300 files; the reviews got through 443 before the hang). Wire into `check`. Print a
  summary table (files, refused, over-width, slowest) so regressions are visible in the log. This is
  the test that would have caught B1 to B6 and A5.
  **Fixed:** `27a069d` — `gradle :javafmt-core:corpusTest` formats 1,276 files from
  `java.base/java/{io,lang,nio,time,util}` out of the toolchain's `lib/src.zip` in 8.8 s (slowest
  file 201 ms, budget 5 s) and holds every count against `corpus-baseline.properties`, which fails
  on a run that is worse *or* better than the recorded value so it cannot drift. Opening baseline:
  0 parse errors, 291 refused, 1 non-reparsing, 0 non-idempotent, 30 files (138 lines) over 150
  characters. Wired into `check`.
  The gate immediately found a new defect, filed below as **A9**: `Executable.java` is the one file
  whose output javac cannot read.

- [x] **C2 Dogfood on a real project** (procedure, run after everything else) — *CLI and Gradle done; the live IntelliJ session is blocked*
  On a branch of one of your own projects, from a clean tree:
  ```
  gradle :javafmt-cli:shadowJar
  alias jf='java -jar /path/to/javafmt-cli-*-all.jar'
  jf --check .                       # must terminate; note every "would reformat" and every diagnostic
  jf .                               # format in place
  git diff --stat                    # eyeball the shape: no whole-file rewrites you did not expect
  jf --check .                       # must be clean: idempotent
  gradle build   # or mvn verify     # compiles and tests pass after formatting
  git diff | grep -E '^[-+]\s*(//|/\*|\*)' | head   # comment lines added/removed should be layout only
  ```
  Any `internal error`, any compile failure, any deleted or moved comment is a new Tier A or B item.
  Then the two clients, on the same project:
  - **Gradle:** wire Spotless per F7 (`publishToMavenLocal` first), run `gradle spotlessCheck`
    (must agree with `jf --check`), `spotlessApply`, and a second `spotlessCheck` that is up-to-date.
    Bump the local javafmt version, confirm `spotlessCheck` re-runs.
  - **IntelliJ:** build the native binary, point Settings | Tools | javafmt at it, enable, reformat
    a file with a utility class (warning-only lint), a file with a parse error, a selection, and
    save with "Reformat code on save". Each must either format or say clearly why not; nothing may
    freeze or silently do nothing.
  Repeat on a second project with a different style (Maven, older code, heavy generics). Record
  the results at the bottom of this file.

---

## Tier D — Output policy: settle it so it stops moving (T5)

Read `review/core-engine.md`. Each item lands with the fixture that pins the decision.

- [x] **D-1 150-char width is not enforced for eight constructs** (M) — *six of eight*
  No wrap points at all in: array initializers (`builder/ExpressionRenderers.java:157`),
  constructor arguments (`:176-181`), annotation arguments (`:250`, and `ModifierRenderer.java:73,83`
  collapses multi-line annotations), type arguments (`builder/TypeRenderers.java:88`),
  try-with-resources headers (`builder/TryRenderer.java:34`), enum constant arguments
  (`builder/EnumRenderer.java:130`), lambda parameter lists (`builder/LambdaRenderer.java:42`),
  deconstruction patterns (`ExpressionRenderers.java:211`). Measured: 162-, 171-, 156-char lines; a
  711-char line in `java/lang/FdLibm.java`. Give each the `Group` + `SoftLine` + `intersperse(",",
  LINE)` + continuation-indent shape `MethodRenderer.buildParamsPart` uses. One over-width fixture
  per construct; then turn on the width assertion in C1.
  **Fixed:** `62332a8` — new `WrappableList` helper (the shape `buildParamsPart` already used) applied
  to array initializers, constructor arguments, try-with-resources headers, enum constant arguments,
  lambda parameter lists, deconstruction patterns and Doc-rendered type-use annotations, each with
  its own over-width pair fixture (`test-fixtures/width-*`). An array initializer's closing brace
  now takes its own line instead of printing `12,};`. **The corpus went from 43 files / 157
  over-width lines to 13 files / 25**, and C1's width count is recorded at 13.
  **Two of the eight are left, deliberately:**
  - *Type arguments.* Implemented, then reverted. A type-argument list sits to the left of the rest
    of the declaration, so its group is decided before the method chain further right has chosen to
    break; the fit check then sees the whole statement and breaks here instead. It turned
    `List<Map.Entry<String, List<Integer>>> ENTRIES = source.stream()…` into `List<` / `Map.Entry<`
    / `String,` / `List<` / `Integer>>> ENTRIES = …`, which is worse than a long line. The comment
    in `TypeRenderers.renderParameterizedType` records why.
  - *Declaration and parameter annotations.* `ModifierRenderer` renders these from source text, not
    as a Doc — the pre-existing open item its own javadoc describes (an author's multi-line
    annotation is collapsed to one line, and a collapsed annotation has no wrap points). Giving them
    wrap points means routing annotations through the Doc pipeline first. The type-use path, which
    *is* Doc-rendered, is fixed and fixtured.
  Of the 25 over-width lines left on the corpus, the rest are comments and string literals (both
  left alone by design) plus two constructs this entry never listed: `case A, B, C ->` label lists
  and `catch (A | B | C e)` union types. Both are straightforward `WrappableList` uses; filed here
  rather than done silently. **Both done in `a6423d8`**, along with the declaration-annotation half
  of the deferral above (`b3e38e7`) — see the 2026-09-24 entry in the dogfood log.

- [x] **D-2 Blank-line rules** (M, D4 and D5 decided)
  Four defects and two policy changes, all in the same area:
  - No blank line between `package` and the first type when there are no imports
    (`builder/ImportSectionRenderer.java:17-19`, `DocBuilder.java:170-187`); in that same case a
    blank line *is* emitted directly after `class A {`, violating "no blanks after `{`".
  - Inside method bodies (D5): keep the author's single blank lines, collapse runs to one, none
    directly after `{` or before `}`. Today all are deleted.
  - Between members (D4, google-java-format's rule): `TypeBodyRenderer.java:27` currently
    intersperses a `HARD_LINE` between every member. Change to: methods, constructors, nested
    types and initializer blocks get exactly one blank line on each side; consecutive fields keep
    the author's zero-or-one blank line, runs collapsed to one; a field with Javadoc counts as a
    method. Blank-line presence in the source is read the way `authorBrokeChain` reads line breaks.
  - Only 2 of 78 idempotent fixtures declare a `package`, which is why none of this was seen.
  Fixtures: import-less file with package; method with paragraph breaks and a triple blank line;
  three fields written adjacent, then two groups separated by a blank line, then a Javadoc'd field
  between plain ones; constants block followed by methods. Each idempotent.
  **Fixed:** `c65e0ac`. Verified against `JavaInputAstVisitor.addBodyDeclarations` first: gjf sets
  `thisOneGetsBlankLineBefore = kind != VARIABLE || hasJavaDoc` and asks for a blank line when
  either that member or the one before it wants one, leaving everything else on `PRESERVE`. The
  plan's description was right and `TypeBodyRenderer.separated` now implements it.
  Method-body blank lines are kept (`BlockRenderer.statementDocs`, used by method bodies too),
  runs collapse to one, and none survive after `{` or before `}`. A `package` with no imports now
  gets its blank line. New fixtures: `idempotent/blank-lines-between-members.java`,
  `idempotent/package-without-imports.java`, pair `blank-line-runs-collapse`; `DocBuilderTest`
  gains adjacent-fields, author-separated-fields and Javadoc'd-field cases.
  **The plan's second bullet was wrong:** it called the blank line after `class A {` a violation of
  "no blanks after `{`". `docs/rules.md` asks for exactly that blank line for a class (the rule is
  about method bodies), so it was left alone.
  Seven existing tests encoded the old behaviour and were rewritten under D4/D5: two pair fixtures,
  `idempotent/trailing-line-comment`, the three `packageDeclaration_*` cases and
  `twoFields_haveBlankLineBetweenThem`, plus one `javafmt-spotless` expectation.
  Corpus unchanged at 29 refused / 13 over-width. `docs/rules.md § Blank lines` rewritten.

- [x] **D-3 Preserve the author's one-line braced `if`** (M, D6 decided)
  `builder/IfRenderer.java`. Braces stay mandatory; `NeedBraces` is untouched. Today every braced
  `if` is expanded to three lines (verified: `if (x > 0) { return; }` became a three-line block).
  Implement author-layout preservation, the same idea `MethodChainRenderer.authorBrokeChain` uses:
  when an `IfTree`'s entire source span (including any `else` and `else if` links) sits on one line,
  every branch is a block containing exactly one statement, each block's source is the compact form
  `{stmt;}` with no whitespace after `{` or before `}`, the span contains no comment, and the line
  fits in 150 chars after indentation, emit the source text verbatim as a `Doc.Text`. Otherwise
  render as today. Preserved shapes:
  ```java
  if (cond) {foo();}
  if (cond) {foo();} else {bar();}
  if (a) {x();} else if (b) {y();} else {z();}
  ```
  Expanded shapes (not the compact form, or not one line, or produced by `NeedBraces`):
  `if (cond) { foo(); }`, `if (cond) {foo();}\nelse {bar();}`, `if (cond) foo();`, and any block
  with two statements. Fixtures: `idempotent/one-line-if.java` with the three preserved shapes (also
  nested inside a lambda and a switch arm, at maximum width); a pair fixture proving each expanded
  shape expands; a pair proving `NeedBraces` output is three-line. Then rewrite the
  `docs/rules.md § Braces` paragraph and the `CLAUDE.md` bullet to state this exact rule.
  **Decided 2026-09-20: `if`/`else` only.** Loop bodies (`for`, enhanced `for`, `while`,
  `do`/`while`) are always expanded to the block form, even when the author wrote `for (...) {i++;}`
  on one line; a loop hides work that deserves its own lines. Add `for (...) {i++;}` and
  `while (c) {step();}` to the expanded-shapes pair fixture.
  **Fixed:** `21362cc` — `idempotent/one-line-if.java` (the three preserved shapes, plus one inside a
  lambda and one inside a switch arm) round-trips where every braced `if` used to become three
  lines; pair fixture `one-line-if-expands` proves spaced braces, an `else` on its own line, two
  statements and both loop forms still expand. The width gate is a
  `Group(IfBreak(expanded, Text(source)))`, so the decision happens at print time with the real
  indent: pushed past 150 characters the same `if` breaks into the block form.
  `docs/rules.md § Braces` and the `CLAUDE.md` single-line-constructs bullet now state the rule
  (the one CLAUDE.md edit this work makes, and only because D-3 asks for it).
  **Tightened during C2:** the dogfood run put `if(in.isEmpty()){return List.of();}` through
  Spotless and got it back untouched — the preserved form is the author's source text verbatim, so
  the missing spaces survived, from a formatter that writes `if (` everywhere else. The preserved
  shape now has to be spaced the way javafmt would space it; `if(c)`, `}else` and `else{` expand.
  Two more cases in the `one-line-if-expands` fixture.

- [x] **D-4 Comment relocation** (L, D8)
  `builder/IfRenderer.java:25-30` hoists a comment between `else` and `if` above the whole statement;
  a trailing `// note` on an argument or parameter becomes a leading comment on the *next* element
  and forces the list to break (`builder/MethodRenderer.java:88`, `builder/MethodChainRenderer.java:105-107`);
  a trailing comment on `{` moves inside the block; a trailing comment inside a chain is hoisted out
  and defeats `authorBrokeChain` (`MethodChainRenderer.java:58-65`: `isFollowedByNewline` sees the
  `/`). The attachment logic is `parser/CommentAttacher.java` (417 lines, not yet audited). Fix
  attachment so a same-line comment stays with the element it trailed, give `IfRenderer` an
  else-position slot, make `isFollowedByNewline` skip a trailing comment. Then rewrite the four
  fixtures that currently defend the relocation: `test-fixtures/comment-else-if/expected.java`,
  `arg-list-interior-comment/expected.java`, `param-list-interior-comment/expected.java`,
  `comment-in-method-chain/expected.java`.

  **Fixed:** `558f809`, `2db4403`, `e582885`, `fd7172a` — the four-defect reproduction now comes back
  as it was written: `} else /* why this branch */ if (x < 0) {`, `first, // about first` on the
  argument's own line, `if (x > 0) { // about the branch`, and `value.first() // about first` with
  the chain still broken across three lines. The attacher gained an else-joint slot, a same-line
  slot for the then-branch's `{`, a chain-joint slot, and a rule that lets a `//` comment cross the
  comma of a parameter or argument list; `isFollowedByNewline` now looks past a comment, so
  `authorBrokeChain` sees the author's break again. The four fixtures that defended the relocation
  were rewritten and five new ones pin the rest. `:javafmt-core:check` is green and the corpus gate
  is unchanged at 29 refused / 13 over-width. A first attempt attached the comment for *any*
  member select, which swallowed the `.add(…)` after `new BigDecimal(…) // Exact multiply` in
  `java/lang/Math.java` and pushed refused to 30; the slot is now limited to the chain shape the
  chain renderer walks link by link.
  **Still open, no plan item covers them:** a comment on the `{` of an `else` block, a loop, or a
  method body still moves inside — only the `if` then-branch is in the reproduction — and
  `new BigDecimal(…) // c` before `.add(…)` still hoists the comment above the statement, which is
  what keeps `Math.java` formatting at all.

- [x] **D-5 Small policy items** (S each) — *five of six; the array trailing comma is deferred*
  Colon-form `case X: { ... }` puts the brace on its own line (`builder/SwitchExpressionRenderer.java:85-95`),
  not K&R; `ArrayTrailingComma.java:41-57` adds the comma unconditionally: per D9 (decided), add
  it only when the initializer's `{` and `}` are on different lines, and remove an existing
  trailing comma from a one-line initializer so the rule is idempotent in both directions (fixture
  pair: one-line stays `{1, 2}`, `{1, 2,}` becomes `{1, 2}`, multi-line gains the comma);
  `ModifierOrder.java:60-62`
  silently skips every annotated declaration (fix, it is the common case); `MissingSwitchDefault.java:8-11,26-33`
  warns on exhaustive pattern switches over sealed types and its javadoc wrongly says arrow-form is
  excluded (skip switches whose labels are all patterns); `ExplicitInitialization` misses
  `char c = '\0'`; an empty `for` update clause renders as `for (a; b; )`, with a stray space
  before the `)` (found during B4).
  **Fixed:** `4057a61` — five of the six.
  - Colon-form `case X: { … }` is K&R (pair fixture `colon-case-braces`); the brace used to take a
    line of its own, the only block in the file that did.
  - `ModifierOrder` now steps over an annotation *before* the keywords instead of giving up on the
    declaration — which is most declarations. An annotation *between* two keywords still stops it,
    because the replacement span would overwrite it. Two `ModifierOrderTest` cases. (An attempt to
    step over comments as well was reverted: it made the replacement span swallow the comment, which
    `LintCompileSafetyTest`'s `modifier-order-comment` case caught.)
  - `MissingSwitchDefault` no longer warns on a switch whose labels are all patterns — the compiler
    checks those for exhaustiveness itself. The javadoc's claim about arrow form is corrected: arrow
    form has nothing to do with it.
  - `ExplicitInitialization` now strips `char c = '\0'`.
  - An empty `for` update clause no longer prints a space before the `)`
    (`idempotent/for-without-update`).

  **Deferred — the array trailing comma (D9's array half).** Implemented three ways and reverted
  each time; the corpus gate caught every one as a **non-idempotency**, which is the defect this
  whole tier exists to prevent. The rule has to know whether the initializer will print on one line,
  and a lint rule cannot: (1) reading the author's source layout fails because javafmt collapses an
  initializer the author broke whenever it fits — first pass adds the comma, second removes it
  (10 corpus files); (2) predicting from the initializer's own column fails because that column
  moves between runs, an initializer on its own line landing at the end of the declaration after
  formatting (2 files); (3) measuring from the enclosing statement's start still missed cases
  (3 files, including `CharacterDataLatin1`). The enum half of the same idea (B5) *is* sound and
  stays, because an enum declaration always starts a line of its own and so its column does not move
  — `SourceLayout`'s javadoc records the distinction.
  Doing this properly needs the comma decided where the line breaking is decided, which means either
  a renderer-emitted comma plus an output verifier that knows an array's trailing comma is
  semantically void, or a two-pass format. Both are bigger than "S" and neither is covered by a plan
  item, so the unconditional comma stands and **D9 is not implemented for arrays**. The owner should
  re-decide with this evidence.

- [x] **D-6 Line endings and BOM** (M, D2 decided)
  `parser/JavaParser.java:385-387` strips a BOM; `builder/CommentDocs.java:18` strips `\r`; the CLI
  reads and writes plain UTF-8 (`Cli.java:199,242`). Detect the dominant line ending (and whether a
  BOM was present) on input and re-apply both on output in core (`Javafmt.formatWithResult`), so the
  CLI, Spotless and IntelliJ all inherit it and a formatted file never shows a whole-file diff.
  Mixed endings normalise to the dominant one. Fixtures: CRLF input asserting CRLF output and
  idempotency; BOM preserved; mixed input normalised; the existing `JavafmtTest.crlfInput_producesLfOnlyOutput`
  is rewritten to the new contract.
  **Fixed:** `7c73a7b` — new `SourceEncoding` record measures the BOM and the dominant ending on the
  way in and puts both back on the way out, on the single-file, analyze and batch paths alike, so
  the CLI, Spotless and IntelliJ all inherit it. `crlfInput_producesLfOnlyOutput` is now
  `crlfInputIsWrittenBackAsCrlf`, joined by idempotency on CRLF, BOM preserved, mixed endings
  going to the majority, a lone `\r` counted as an ending, and a batch run keeping each file's
  own endings. `docs/rules.md` gains a *Line endings and byte-order marks* section.

- [x] **D-7 Nested types to the bottom; utility constructor last of all** (M, D11 decided)
  Today `builder/UtilityClassConstructor.java` moves the utility-class constructor to the bottom
  inside the renderer. Implement both reorders as one lint rule (`MemberOrder`) that rewrites the
  source before rendering, and retire the renderer-side move. Reason: A6 makes `RoundTripVerifier`
  compare token *sequences*, so a reorder performed after lint would be reported as corruption; a
  lint edit lands in the post-lint source the verifier uses as its baseline, the same pattern as B5
  and B6. Rule: within each class, interface, enum or record body, take every member that is a
  nested type (`ClassTree` of any kind) and append them after the last non-type member, preserving
  their relative order; then, if the class is a utility class by the existing predicate (`final`,
  no instance state, only a `private` no-arg constructor), append that constructor after the nested
  types. Each moved member carries its leading comments and Javadoc (use `CommentScanner` to find
  the span start) and its trailing same-line comment. Blank-line separation follows D4. Nested types
  are safe to move: they execute no code and can be referenced from anywhere in the enclosing body.
  Do not reorder enum constants, fields, methods, or initializer blocks; do not reorder inside
  anonymous class bodies or local classes. Fixtures: one nested class between fields; three nested
  types of mixed kinds keep their order; a nested type with Javadoc and a trailing comment; a
  utility class with a nested type (constructor ends up last); a class already in order (idempotent);
  an enum with constants, a nested record and methods; an interface with a nested class. Update
  `docs/rules.md § Member ordering` in the same commit.
  **Fixed:** `38a76e7` — new `MemberOrder` lint rule; `UtilityClassConstructor` and its renderer-side
  move are deleted. Fixtures: pair `member-order-nested-types` (a record with Javadoc *and* a
  trailing comment, an interface and an enum all move down in order, with the private constructor
  last of all and fields untouched), `member-order-enum` (constants and their `;` stay put),
  `member-order-interface`, `idempotent/member-order-already-sorted`,
  `idempotent/member-order-anonymous-class-untouched`. Five existing idempotent fixtures
  (`sealed-hierarchy`, `pattern-any`, `pattern-deconstruction`, `pattern-deconstruction-var`,
  `inner-class-creation`) encoded the old no-reorder shape and were rewritten under D11.
  **Caught during the work:** the first version refused 58 corpus files instead of 29 with
  `overlapping lint edits`. A nested type that is moving is copied wholesale, so the rule's own
  edits *inside* that type landed in a span the same round was deleting. The scan now stops at a
  class it has just reordered and lets the engine's next round handle the inner body. Corpus back to
  29 refused / 13 over-width, unchanged. `docs/rules.md § Member ordering` rewritten.

---

## Tier E — Safe to run from the CLI and Spotless (T6)

Read `review/cli.md` and `review/spotless-maven.md`. Files are
`javafmt-cli/src/main/java/io/javafmt/cli/Cli.java` unless noted.

- [x] **E1 In-place writes are truncate-then-write** (S)
  `:241-246` (`Files.writeString`), and `javafmt-maven-plugin/.../JavafmtMojo.java:54-59`. A crash
  or Ctrl-C between truncate and flush leaves the source file empty. `cache/FileFormatCache.save()`
  already does temp-file + `ATOMIC_MOVE`; do the same here, preserving POSIX permissions. Tests:
  permissions preserved, no temp file left behind.
  **Fixed:** `b87f7b2` — `SourceFilesTest.writeSwapsANewFileIntoPlaceInsteadOfTruncatingTheOldOne`
  failed at HEAD with `Expecting actual: (dev=1000012,ino=18681384) not to be equal to:
  (dev=1000012,ino=18681384)` — the same inode, so the old file had been truncated in place. Both
  the CLI and the `format` mojo now call the new `SourceFiles.write`, which builds the content in a
  sibling temp file and `ATOMIC_MOVE`s it over the target. Permissions are carried onto the
  replacement, a symlink argument is resolved first so the link survives, and a file the
  permissions say is not writable is still refused with `AccessDeniedException`.

- [x] **E2 No default excludes** (M)
  `javafmt-core/.../SourceFiles.java:20-24`. `javafmt .` reformats `build/`, `target/`, `out/`,
  `node_modules/`, `.git/`, `**/generated/**`. Add a default exclude set to `PathFilter` applied
  during directory expansion, with `--no-default-excludes`; use it from Spotless and Maven too.
  Test: a tree with `build/generated/Gen.java` and `src/S.java` formats only `S.java`.
  **Fixed:** `0dfcfcd` — `javafmt --check <tree>` listed `build/generated/Gen.java`,
  `node_modules/N.java`, `out/O.java`, `src/main/java/S.java` and `target/generated-sources/T.java`
  at HEAD; it now lists `src/main/java/S.java` alone, and `--no-default-excludes` brings all five
  back. The set is `PathFilter.DEFAULT_EXCLUDED_DIRECTORIES` = `.git`, `build`, `generated`,
  `node_modules`, `out`, `target`, matched as directory names *below* the root the caller named, so
  `javafmt build` still formats what is inside `build`. `SourceFiles.walk` applies it, which gives
  the CLI and both mojos the same behaviour; `docs/intro.md` documents it. Nothing changed in
  `javafmt-spotless`: the Spotless step formats a string Spotless has already selected, so there is
  no directory expansion there to filter.

- [x] **E3 An unreadable directory silently aborts discovery and `--check` exits 0** (S)
  `SourceFiles.java:20-27` (`Files.walk` throws `UncheckedIOException` mid-walk, not the caught
  `IOException`), `Cli.java:141-149` returns `Stream.empty()`. One `chmod 000` subdirectory: nothing
  formatted, exit 0 in check mode. Use `Files.walkFileTree` with `visitFileFailed` that records and
  continues; return a discovery outcome so `run` exits 2 when any root failed.
  **Fixed:** `edb59e5` — with one `chmod 000` subdirectory beside an unformatted `B.java`,
  `javafmt --check <tree>` printed only `cannot walk directory: … AccessDeniedException` and
  `exit=0` at HEAD. It now prints that line *and* `B.java: would reformat`, and `exit=2`.
  `SourceFiles.discover` walks with `Files.walkFileTree`, records each failed entry in
  `Discovery.unreadable()` and carries on; `SourceFiles.walk` (the mojos' entry point) rethrows the
  first failure, so an unreadable directory still fails a Maven build rather than passing quietly.

- [x] **E4 Warnings are discarded in file mode, and file diagnostics lose `line:col`** (S)
  `:219-254`: `reportDiagnostics` is only called from `runStdin`, so `javafmt --check Foo.java`
  prints nothing where stdin mode prints the `FallThrough`/`EqualsHashCode` warnings and the
  `--analyze` "attribution failed" warning. `:226-232` prints only the first error as
  `path: message`. Emit every diagnostic via `Diagnostic.describe(path)` in text and JSON modes; exit
  code unchanged for warnings. Tests in `CliTest`.
  **Fixed:** `70be5d8` — `javafmt --check Fall.java Broken.java` printed only
  `Fall.java: would reformat` and `Broken.java: illegal start of expression` at HEAD. It now prints
  `Fall.java:2:14: warning: parameter 'n' is reassigned…`, `Fall.java:4:13: warning: case falls
  through…`, `Fall.java:1:1: warning: class overrides equals(Object) but not hashCode()`,
  `Fall.java: would reformat` and `Broken.java:3:17: error: illegal start of expression`, exit 1 —
  the same three warnings stdin mode prints. `FileOutcome` now carries every line to print rather
  than one optional string, built from `Diagnostic.describe(path)` in text mode and
  `JsonDiagnostics.line` in JSON mode; the exit code is unchanged by a warning.

- [x] **E5 Cache key ignores the config** (S)
  `cache/FileFormatCache.java:43` keys on the javafmt version only. A plain run followed by
  `--analyze` skips every file, so the analyze-only rewrites never happen and it looks like javafmt
  did nothing. Fold a hash of the effective config (analyze, classpath, `lint`, `verifyOutput`) into
  the header; give `save()`'s temp file (`:135`) a unique suffix.
  **Fixed:** `329df26` — `javafmt --cache c W2.java` followed by
  `javafmt --cache c --analyze --classpath "" W2.java` printed nothing on the second run at HEAD
  (every file a cache hit); both runs now print
  `W2.java:1:1: warning: class overrides equals(Object) but not hashCode()`. The header is now
  `# javafmt cache v3 <version> <fingerprint>`, e.g.
  `# javafmt cache v3 0.1.0-…-SNAPSHOT 34a1933579b859b5`, where the fingerprint is a SHA-256 over
  the new `CacheKey` (`lint`, `verifyOutput`, `analyze`, classpath). `excludes` is left out on
  purpose: it picks which files a run visits, not what any one of them formats to. `save()` now
  takes its temp file from `Files.createTempFile`, so two runs sharing a cache path cannot write
  over each other's half-built file.

- [x] **E6 Argument and error-message hygiene** (S)
  `:337-350`: `--` is "unknown flag", `-` is "read failed: -", unknown `-x` is treated as a path;
  `:69-71` blocks on a TTY with no arguments (print usage, exit 2); `:201-206,243-245`: map
  `MalformedInputException` to "not valid UTF-8", `AccessDeniedException` to "permission denied",
  `NoSuchFileException` to "no such file". Tests.
  **Fixed:** `d06490c` — at HEAD `javafmt -- W2.java` said `unknown flag: --`, `javafmt - < D.java`
  said `-: read failed: -`, `javafmt -x` said `-x: read failed: -x`, and two bad files reported
  `read failed: Input length = 1` and `read failed: Missing.java`. Now `javafmt --check -- W2.java`
  exits 0, `javafmt - < D.java` prints `class D {}`, `javafmt -x` says `javafmt: unknown flag: -x`
  (exit 2), and the same two files report `read failed: not valid UTF-8` and
  `read failed: no such file`; an unreadable file reports `read failed: permission denied`. With no
  arguments and a terminal on stdin, `run` prints the usage and exits 2 instead of blocking —
  `Cli.run` takes the terminal answer as a parameter so a test can state it, since a test harness
  never has one. `-` combined with file arguments is a usage error rather than undefined behaviour.
  The matching rows are in `--help` and `docs/intro.md`.

---

## Tier F — The clients you will use: IntelliJ and Gradle (T7)

Read `review/intellij.md` and `review/spotless-maven.md`. The IntelliJ plugin does not call
`javafmt-core` in-process; it execs a native `javafmt` binary (core is class-file 69, IDEA's JBR is
21; enforced by `BytecodeTargetTest`). So for local use you need `gradle :javafmt-cli:nativeCompile
--no-configuration-cache` and the plugin pointed at `javafmt-cli/build/native/nativeCompile/javafmt`.

### IntelliJ (`javafmt-intellij/src/main/java/io/javafmt/intellij/`)

- [x] **F1 Any warning-only lint diagnostic discards the format** (S) — **do first in this tier**
  `JavafmtExternalFormatProcessor.java:121-124`: `if (!formatted.diagnostics().isEmpty()) { report;
  return null; }`. The CLI emits `Diagnostic.Warning` as `{"kind":"warning"}` and still exits 0 with
  the formatted source, but the plugin treats the list as failure, leaves the file untouched and
  shows "was left unformatted". `HideUtilityClassConstructor`, `MissingSwitchDefault`,
  `AvoidStarImport`, `FallThrough`, `EqualsHashCode` all trigger it, so Reformat Code stops working
  on a large share of ordinary files. Gate on `kind` in `{parse, lint, internal}`; install
  `formatted.output()` when only warnings are present. Test with the fake binary emitting a warning.

  **Fixed:** `6706912` — the new fixture test failed at HEAD with `Expecting actual not to be null`
  and a balloon reading `Warned.java was left unformatted: utility class has no private constructor`;
  gating on `Formatted.hasFailure()` (any `kind` other than `warning`) installs the output instead, and
  `gradle :javafmt-intellij:test` reports `"testsRun" : 76, "failures" : 0`. Gated on *not* `warning`
  rather than on `{parse, lint, internal}` as written, so `cli` — and any kind added later — still
  refuses rather than installing output of unknown standing.

- [x] **F2 The version gate rejects every 0.1.x binary, including your local build** (S)
  `JavafmtBinary.java:61-62,165-174`: `MIN_MINOR = 2`; a local build reports
  `javafmt 0.1.0-feature-javafmt-improvements-SNAPSHOT`, so the plugin says the binary is "too old"
  and never formats. The regex `javafmt (\d+)\.(\d+)\.` also rejects `javafmt 1.0`. Derive the
  minimum from the plugin's own build version (inject `project.version` via a generated resource) and
  accept two- to four-component versions with any suffix. Test pinning gate == build version.

  **Fixed:** `aead92e` — `theGateAcceptsABinaryReportingThePluginsOwnBuildVersion` failed at HEAD with
  `Expecting value to be true but was false` for `javafmt 0.1.0-...-SNAPSHOT`. The gate now compares
  against a generated `JavafmtPluginVersion.VERSION` (the build stamps the same string into a
  `javafmt.buildVersion` system property, so the test pin is independent of the generated source) and
  parses two- to four-component versions with any suffix. `gradle :javafmt-intellij:check` reports
  `"testsRun" : 84, "failures" : 0`. Generated a Java source, as `javafmt-core` does for
  `JavafmtVersion`, rather than a properties resource: no runtime IO and no missing-file fallback.

- [x] **F3 Subprocess spawned and awaited on the EDT inside a write action** (M)
  `JavafmtBinary.java:52,96,108,179,195`, `JavafmtExternalFormatProcessor.java:99-111`. Verified by
  disassembling IDEA 2025.2: `ExternalFormatProcessor.format` runs synchronously inside
  `CodeStyleManagerImpl.reformatText` on the EDT with the write lock held. The plugin spawns twice on
  the first reformat (`--version` probe, then format) and waits up to 10 s with no cancellation; a
  slow binary freezes the IDE. `JavafmtConfigurable.java:59-65,93-107` also probes on the EDT when
  Settings opens. Migrate to `com.intellij.formatting.service.AsyncDocumentFormattingService`
  (off-EDT with progress and cancellation; result applied in a write action by the platform), cache
  the version probe per binary, move the Settings probe to a pooled thread with `invokeLater`.

  **Fixed:** `e7a9587` — at HEAD `FormattingServiceUtil.findService(file, true, true)` returned
  `com.intellij.formatting.service.ExternalFormatProcessorAdapter` (the synchronous adapter) and
  `JavafmtConfigurable.reset()` against a binary that sleeps 3 s took `3.206762708S` on the EDT. The
  processor is now `JavafmtFormattingService extends AsyncDocumentFormattingService` (registered as
  `<formattingService>`), its `FormattingTask.cancel()` interrupts the worker so `Process.waitFor`
  gives up and kills the child, and the Settings probe runs on `AppExecutorUtil` with the label filled
  in via `invokeLater`. Both tests pass and the whole suite is `"testsRun" : 81, "failures" : 0`.
  The test class now drives `CodeStyleManager.reformatText` rather than the processor directly, which
  is the end-to-end coverage F5 asks for.

- [x] **F4 Binary lookup misses Homebrew and SDKMAN on macOS** (S)
  `JavafmtBinary.java:79-84` reads `System.getenv("PATH")`, which for a Dock-launched IDE is
  launchd's minimal environment (no `/opt/homebrew/bin`, no `~/.sdkman`). Use
  `PathEnvironmentVariableUtil.findInPath("javafmt")` with `EnvironmentUtil` as fallback. Keep the
  configured-path branch. Test with a fake PATH.

  **Fixed:** `843ff8c` — `resolve("")` now searches
  `PathEnvironmentVariableUtil.getPathVariableValue()` (i.e. `EnvironmentUtil.getValue("PATH")`, the
  login shell's) through a package-private `resolveOnPath` seam, so a test can search a PATH of its
  own: `resolveFindsAnExecutableOnAPathTheJvmsEnvironmentDoesNotHave` asserts the temp directory is
  not on `System.getenv("PATH")` and is still searched. Four new tests; suite
  `"testsRun" : 85, "failures" : 0`. The red was a compile failure on the new seam plus
  `theSearchedPathIsTheIdesRatherThanTheJvmsOwnEnvironment`, since the old code's PATH source cannot
  be varied from a test at all — which is the defect.

- [x] **F5 Selection reformat and missing-binary "fallback" are silent no-ops** (S)
  `build.gradle.kts:62-72` (Marketplace text), `JavafmtConfigurable.java:75-77`,
  `JavafmtExternalFormatProcessor.java:82-98`. `FormattingServiceUtil.findService` picks the first
  service whose `canFormat` is true and never chains to `CoreFormattingService`, so returning `null`
  means nothing happens. Reformat Code on a selection therefore does nothing while javafmt is
  enabled. Either delegate explicitly for partial ranges or make `activeForFile` honest and fix the
  two texts. Add one end-to-end test through `CodeStyleManager.getInstance(project).reformatText(...)`
  for whole-file and for a selection; every current test calls `processor.format` directly
  (`JavafmtExternalFormatProcessorTest.java:318-330`), which is why F1 and F5 survived.

  **Fixed:** `f050f21` — `ExternalFormatProcessorAdapter.FEATURES` is
  `EnumSet.of(AD_HOC_FORMATTING, FORMAT_FRAGMENTS)` (disassembled), so it claimed every selection and
  then discarded the processor's `null`. `JavafmtFormattingService.getFeatures()` returns `Set.of()`,
  and `FormattingServiceUtil.findService`'s predicate skips a service that lacks the feature the
  request needs, so a selection now reaches `CoreFormattingService`:
  `testSelectionReformatGoesToIntellijsOwnFormatter` reformats `class Foo {\nint x;\n}` over the
  field's range and gets `class Foo {\n    int x;\n}` — IntelliJ's indentation, not a no-op. The
  missing-binary claim was false and stayed false, so the Marketplace description and the settings
  hint now say the file is left exactly as it was with a notification. Suite
  `"testsRun" : 88, "failures" : 0`.

- [x] **F6 IntelliJ's own Optimize Imports fights javafmt's import order** (M)
  The adapter contributes no `ImportOptimizer`, so Ctrl+Alt+O runs IntelliJ's optimizer (static
  imports last, `java.*` grouping) and javafmt immediately rewrites it back. Contribute an
  `ImportOptimizer` that delegates to javafmt, or at least document the ping-pong in the settings
  panel. Lower priority than F1 to F5 but it is the interaction you will hit daily.

  **Fixed:** `3c602b8` — took the documented option, deliberately.
  `OptimizeImportsProcessor.prepareTask` calls `ImportOptimizer.processFile` from a `FutureTask` the
  layout processor runs under the write action on the EDT (disassembled: it opens with
  `Application.assertReadAccessAllowed`), so an optimizer delegating to the binary would put the
  subprocess back on the EDT and undo F3. The settings panel now states the disagreement (static
  imports first in one ASCII-sorted group vs IntelliJ's static-last, `java.*`-grouped layout) and
  `theSettingsPanelSaysOptimizeImportsDisagreesWithJavafmt` pins it — it failed at HEAD with
  `Expecting any element of: [...] to contain "Optimize Imports"`. Suite
  `"testsRun" : 89, "failures" : 0`. Worth revisiting if the platform ever runs `processFile`
  off the EDT.

### Gradle via Spotless (`javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java`)

- [x] **F7 The documented wiring defeats up-to-date checking; use `create()`** (S)
  `docs/intro.md:24-32` shows `custom("javafmt") { apply(input) }`. Spotless keys a `custom` step's
  state on `bumpThisNumberIfACustomStepChanges`, not on the javafmt version (verified by decompiling
  `FormatExtension.custom`), so after upgrading javafmt your projects keep passing `spotlessCheck` with
  stale formatting. The step's own `create()` (`:19-33,86-101`) keys on `JavafmtVersion.VERSION`.
  Wire your projects as
  `spotless { java { addStep(io.javafmt.spotless.JavafmtFormatterStep.create()) } }` with
  `io.javafmt:javafmt-spotless` on the buildscript classpath (from `gradle publishToMavenLocal` until
  Central exists), and fix the snippet. Change `javafmt-spotless/build.gradle.kts:9` to
  `api(project(":javafmt-core"))` so `JavafmtConfig` resolves for consumers.

  **Fixed:** `9b4c523` — `gradle :javafmt-spotless:generatePomFileForMavenJavaPublication` produced
  `<artifactId>javafmt-core</artifactId> ... <scope>runtime</scope>` before the change and
  `<scope>compile</scope>` after, so `JavafmtConfig` is now on a consumer's compile classpath.
  `docs/intro.md` shows the buildscript classpath, the Spotless plugin and
  `addStep(JavafmtFormatterStep.create())`, and says in one line why `custom("javafmt")` leaves
  `spotlessCheck` passing on stale formatting. The durable regression test is F10's TestKit run,
  which wires a consumer build exactly this way.

- [x] **F8 Spotless never shows javafmt's warnings** (M)
  `:54-64,73-84` throws on `result.errors()` and drops `result.warnings()`. Implement
  `FormatterStep.lint(String, File)` (a default method in `spotless-lib` 4.5.0) mapping warnings to
  `com.diffplug.spotless.Lint`, so `spotlessCheck` surfaces `FallThrough`, `EqualsHashCode` and the
  rest instead of hiding them.

  **Fixed:** `8726065` — `lintReportsJavafmtsWarningsSoSpotlessCanShowThem` failed at HEAD with
  `Expecting actual not to be empty` (the interface's default `lint` returns nothing). `create()` now
  returns a `LintingStep` record that delegates `format`, equality and serialization to the step
  Spotless built — so the existing version/config invalidation tests still pass — and implements
  `lint` by mapping `FormatResult.warnings()` onto `Lint.atLine(line, "javafmt", message)`, skipping
  excluded files. `gradle :javafmt-spotless:check` reports `"testsRun" : 25, "failures" : 0`.
  Note: `lint` formats the source a second time, since javafmt has no warnings-only entry point.

- [x] **F9 Analyze-dependent rules are unreachable from Spotless** (S)
  `applyWithAnalyze(String, String)` (`:45`) is reachable only through the reflective bridge in
  `buildSrc/src/main/kotlin/JavafmtFormatter.kt`. Add `create(JavafmtConfig, String analysisClasspath)`
  (or `JavafmtConfig.withAnalysisClasspath`) so your projects can enable the same rules javafmt
  applies to itself.

  **Fixed:** `e89a119` — `createWithAnalysisClasspathRunsTheRulesThatNeedResolvedTypes` failed at HEAD
  (the step kept `Stream.<String>of("a")`); with `create(JavafmtConfig.defaults(), java.class.path)`
  it emits `Stream.of("a")`, which only `RedundantTypeArguments`' analyze path can do. The classpath
  is part of `State`, so a changed classpath invalidates the up-to-date check like the version and
  config do. The package-private version seam is renamed `createForVersion(config, classpath,
  version)`: it had the same shape as the new public overload, and the first run of the new test
  silently bound `"/one"` to *formatterVersion* and passed for the wrong reason.
  `gradle :javafmt-spotless:check` reports `"testsRun" : 27, "failures" : 0`; `docs/intro.md` shows
  the wiring.

- [x] **F10 A Gradle TestKit integration test** (M)
  Nothing runs real Spotless; every test calls `step.format(...)`. Add an `integrationTest` source
  set that applies the Spotless plugin, adds the step via `create()`, runs `spotlessApply`, asserts
  output, asserts a second run is up-to-date, asserts a bumped javafmt version invalidates, and runs
  with the configuration cache on (the `FormatterFunc` serialization hazard noted in
  `review/spotless-maven.md` is unverified).

  **Fixed:** `06fc507` — `javafmt-spotless` gains an `integrationTest` source set that `check` depends
  on. `SpotlessIntegrationTest` generates a Gradle project wired the way `docs/intro.md` says, runs it
  with TestKit and asserts: `spotlessApply` formats the file; a second run is `UP_TO_DATE`; pinning a
  different javafmt version makes it `SUCCESS` again; `--configuration-cache` stores an entry with no
  problems and reuses it on the next run (so the `FormatterFunc` serialization hazard is not real
  here); and `spotlessCheck` fails reporting the `AvoidStarImport` warning, which is F8 proven
  end to end. `gradle :javafmt-spotless:integrationTest` reports `"testsRun" : 5, "failures" : 0`.
  The generated build resolves nothing: javafmt, its dependencies and the Spotless plugin are
  injected as files on the buildscript classpath, so the run is offline and pinned to the code under
  test.

---

## Parked

Everything about publishing (JReleaser wiring, Maven-plugin publication, native binaries on
releases, both marketplaces), the VS Code extension and Maven plugin bugs, the public API freeze
(`module-info`, 46 needlessly public types), docs, README, community files and CI hardening is in
`review/release-backlog.md` with the same level of detail. None of it changes what the formatter
does to a file or how it behaves from IntelliJ or Gradle.

---

## Dogfood log

### 2026-09-20 — javafmt itself (274 Java sources, 5 modules)

`javafmt --check` over every `src/**/java` file in `javafmt-core`, `javafmt-cli`, `javafmt-spotless`,
`javafmt-intellij`, `javafmt-maven-plugin` and `javafmt-vscode`: **274 files, 0 would reformat,
0 refused, exit 0** — the tree is at the formatter's own fixed point.
`gradle :javafmt-core:check :javafmt-cli:check :javafmt-spotless:check :javafmt-intellij:check
:javafmt-maven-plugin:check` → **1,249+ tests, 0 failures**, including the corpus gate. So
"compiles and its tests pass after formatting" is verified on this project.

### 2026-09-20 — a second, unformatted project (168 Java files)

The owner's other repository had uncommitted work on an active branch, so its Java sources were
copied to a scratch tree and formatted there; **its build and tests were not run**, and that half of
the procedure is unverified for this project. What was:

| | |
|---|---|
| Files | 168, none of which had ever seen javafmt |
| First `--check` | 92 would reformat, **1 refused**, 0 parse errors, 0.53 s |
| The one refusal | `instanceof final String proto` — filed and fixed as **B8** |
| After the fix, format in place | **0 refused**, 93 of 168 files changed, +2,189 / −2,079 lines |
| Second `--check` | **silent, exit 0** — idempotent |
| Comment diff | **not one line of the diff contains a comment**; the `//` count is identical before and after |

### Gradle via Spotless

`gradle publishToMavenLocal`, then a scratch project wired exactly as F7 documents —
`addStep(io.javafmt.spotless.JavafmtFormatterStep.create())` with `io.javafmt:javafmt-spotless` on
the buildscript classpath. `spotlessCheck` reported the violation the CLI reported; `spotlessApply`
produced a file the CLI then accepted with **exit 0**, so the two agree; a second `spotlessCheck`
was **UP-TO-DATE**. Version invalidation is covered by the F10 TestKit suite (5 tests, green), which
also asserts the configuration cache stores and reuses.

**Worth knowing:** the consuming build's Gradle daemon must run on **Java 25**. javafmt-core is
class-file 69 and the Spotless step loads it in the daemon JVM, so on a Java 21 daemon the build
fails with *"Dependency requires at least JVM runtime version 25"* before javafmt runs at all. The
scratch project needed `gradle/gradle-daemon-jvm.properties`. This is not in `docs/intro.md`.

### IntelliJ

`gradle :javafmt-cli:nativeCompile --no-configuration-cache` with GraalVM 25.0.2 produced a working
binary (`javafmt 0.1.0-feature-javafmt-improvements-SNAPSHOT`). Formatting the 168-file project with
the **native binary produced byte-identical output to the jar** (`diff -rq` → no differences), and
`--check` on the result exited 0. `JavafmtBinaryTest` (34 tests) confirms the version gate accepts
this binary, which is F2's fix.

**Blocked:** the live-IDE half — pointing Settings | Tools | javafmt at the binary and reformatting
a utility class, a parse-error file and a selection, then saving with "Reformat code on save" —
needs an interactive IDEA session and cannot be run from here. Every one of those paths is covered
by a test through the platform's own entry point (F1, F3, F5), but no real IDE was exercised.

### 2026-09-20 — working the corpus refusals down from 29

After the plan closed, the remaining refusals were worked through as their own pass. **29 → 15.**

| Fixed | Files | What it was |
|---|---|---|
| `0x7E-ch` lexed as one token | 1 | The verifier's lexer treated `E` as an exponent marker in a *hex* literal, where it is a digit. `ASCII.java` was a **false refusal** — javafmt had formatted it correctly and then refused its own output. |
| Type-parameter annotations dropped | 6 | `class Box<@Marker T>` — `renderTypeParameter` never read `getAnnotations()`. The `java.lang.ref` family all use `@jdk.internal.RequiresIdentity`. |
| Lambda parentheses and parameter modifiers dropped | 4+ | `(t) -> …` printed as `t -> …` and `(final String t) -> …` lost its `final`. `Predicate` and its three primitive siblings. |
| A terminator swallowed by a trailing comment | — | `+ "zul" // Zulu` followed by `;` on the next line printed as `// Zulu;`. With verification off that output **does not compile**; the verifier was the only thing stopping it. |
| A comment emitted twice | 1 | javac constant-folds `"aa" + "AAA" // Alpha` and everything below it into a single `LiteralTree` spanning the whole expression, which the renderer prints verbatim from source — and the attacher emitted the same comments again. `LocaleISOData.java` came out with each of its 437 comments twice. |

**Attempted and reverted: rendering a multi-variable field group** (would have been ~11 of the 15).
`MultipleVariableDeclarations` correctly declines to split `static final @Stable AccessFlag[] // Can use stable array …` because rebuilding the span would delete the comment (the A4 fix), and the renderer then prints each declarator in full — repeating the type and losing the commas. Rendering the group as one declaration works, but each declarator's leading comment is attached to its *initializer*, so it lands after the `=`, and a multi-line block comment there re-indents differently on every pass: **2 files non-idempotent, and 5 after attaching each declarator separately.** Trading a safe refusal for a non-idempotent formatter is the wrong way round, so it is reverted. The real fix is in `CommentAttacher`: a comment before a declarator's *name* needs its own slot, which is the same class of work D-4 did for the else-joint and the chain-joint.

**The 15 that remain** are: 11 multi-variable field groups (above), 3 with a comment the renderer drops
(`StackStreamFactory`, `DateTimeFormatterBuilder`, `StampedLock`), and `CharacterDataLatin1`, which
gets a trailing comma the source does not have. (Diagnosed here as the D9 deferral showing through,
which was wrong — see the 2026-09-21 entry below. D9 is about *when* the comma is added; this was
`ArrayTrailingComma` failing to notice the one already there.) None of them corrupts a file: every
one is returned unchanged with a diagnostic.

### 2026-09-20 — the multi-variable field groups, **15 → 7**

The reverted attempt above asked the renderer to print a group javac had already split into
declarators. The fix instead makes the *source* say what the renderer prints, which is the same
principle as B5, B6, D-7 and A6: **every normalisation is a lint edit, so the verifier's baseline
already contains it.** `MultipleVariableDeclarations` no longer rebuilds the declaration from
modifiers, type, names and initializers — it replaces each separating `,` with `;` and writes a copy
of the shared prefix in front of the next name. Nothing else in the span is retyped, so the
declarators, their initializers, the author's comments and line breaks all come through as written,
and the rule stopped having to decline on sight of a comment.

Getting there cost four bugs, each caught by the corpus gate rather than by a fixture:

| Bug | Files | What it was |
|---|---|---|
| `countBaseDimensions` bailed at a comment | 11 | A comment between the type and the first name read as neither whitespace nor a dimension, so the rule returned -1 and left the whole group alone. |
| Generic-argument commas counted as separators | 5 | `Node<K, V> first, e;` has two commas and only the second separates. Fixed by walking *backwards* from each declarator's end — javac puts that end past the comma, so the separator is found first. |
| A comment before a declarator's name landed after its `=` | 1 | `CommentAttacher.visitVariable` started the initializer's leading slot at the declaration, so a comment written before the name was attached to the initializer. Its slot now starts after the declared name. `ZipCoder.java` was non-idempotent from the block comment re-indenting on every pass. |
| The copied prefix ran into the next name | 1 | `double a,t;` became `doublet;`, and a trailing `// comment` after the comma became `double // comment[] T = {`. The prefix now carries its own space and is written *after* any comment following the comma, so the comment stays on the line of the declaration it was written against. `FdLibm.java` writes all of its constant tables this way. |

**The 7 that remain** are the 3 comment-drop files, `CharacterDataLatin1` (the D9 deferral), and 3
where two lint rules claim overlapping spans in one round and `applyEdits` throws
(`MethodHandleInfo`, `ClassSpecializer`, `AccessFlag`). None corrupts a file: every one is returned
unchanged with a diagnostic. Corpus gate at `a58f5e0`: **1,276 files, 0 unparseable, 7 refused, 0
non-reparsing, 0 non-idempotent, 11 over width.**

### 2026-09-21 — the overlapping lint edits, **7 → 4**

Not one bug but the same bug in three rules. Every declarator of `int a = 1, b = 2;` is its own
`VariableTree` carrying the *same* modifier list and the *same* type span, so a rule that rewrites
one of those shared spans emits its edit once per declarator. `LintEngine.applyEdits` rejects the
set as overlapping and the file is refused.

| Rule | File | The shared span |
|---|---|---|
| `RedundantModifier` | `MethodHandleInfo` | The modifier list of an interface field group — nine `REF_` constants under one `public static final int`. |
| `ModifierOrder` | `AccessFlag` | The modifier list of `private static final @Stable AccessFlag[] CLASS_FLAGS = …, FIELD_FLAGS = …`. |
| `LocalVarUseVar` | `ClassSpecializer` | The type of `final Var NO_THIS = …, AFTER_THIS = …, IN_HEAP = …`. |

Deduplicating the identical edits would have been wrong: `var` is not legal in a compound
declaration, so `LocalVarUseVar`'s edit is not merely repeated, it is unsound. All three rules now
decline on a declaration group, which `MultipleVariableDeclarations.isPartOfDeclarationGroup`
already answered for `ArrayTypeStyle`. The group is split on the same round, so each declaration is
its own candidate on the next one — the deferral `LintEngine` already documents for two rules that
want one span.

**A second bug, exposed by the first.** With `AccessFlag` no longer refused it turned out to be
non-idempotent. An enum whose constants are followed by declarations needs a `;`, and the renderer
wrote it directly after the last constant — but `attach` puts a trailing comment after everything
else on the line, so the output was `MODULE_OPENS(…); // did not exist`. A comment after that `;`
belongs to the next declaration, not to the constant, which is exactly where the next pass put it.
The `;` now goes on a line of its own whenever the last constant carries a trailing comment, the
same rule `FieldRenderer.terminator` applies to a declaration ending in a `//` comment. Nothing is
invented: `EnumTrailingComma` has already removed any `,` before the `;` by then.

**The 4 that remain** are the 3 comment-drop files and `CharacterDataLatin1`. Corpus gate at
`6d8d598`: **1,276 files, 0 unparseable, 4 refused, 0 non-reparsing, 0 non-idempotent, 13 over
width.** Over-width rose by two files because `ClassSpecializer` and `MethodHandleInfo` are now
formatted at all; their four long lines are two indented comments (left alone in v1) and two
declarations a couple of characters past the width.

### 2026-09-21 — the last four refusals, **4 → 0**

**Three were one bug in five places.** A container hands its renderer trailing comments through two
channels — `tail`, for what follows its last child, and `interior`, for a container that has no
children at all — and `CommentAttacher` never fills both for the same node. So a renderer that asks
for one and not the other is correct right up until the container happens to be empty, or happens
not to be. Six renderers asked for exactly one:

| Container | Asked for | Dropped |
|---|---|---|
| class and interface body | `tail` | `static final class WriterNode extends Node { // node for writers` — `StampedLock` |
| enum with no body declarations | neither | `CLOSED; // the stream is closed` — `StackStreamFactory` |
| initializer block | `interior` | 13 comments closing a `static {}` — `DateTimeFormatterBuilder` |
| record body | `tail` | not yet seen in the wild |
| bare nested block | `interior` | not yet seen in the wild |
| method body | both | — |

`LeadingCommentAttacher.trailingInside` now answers the question once and every container renderer
goes through it. `BlockRenderer.buildBlock` also had to start writing those comments *after* the
statements instead of before: with only empty blocks reaching it the order never showed, and the
first thing the fix did was hoist `DateTimeFormatterBuilder`'s closing notes above the first
statement of its initializer.

Writing the tests first turned up two more shapes of the same bug that `java.base` does not
contain — an instance initializer block and an empty interface body — so `CommentAuditTest` covers
five.

**The fourth was not what the earlier note said it was.** `CharacterDataLatin1` was recorded as the
D9 deferral showing through. It was not. `ArrayTrailingComma` decides whether an array already ends
in a comma by looking at the raw text between the last element and the `}`, and that file writes

```java
    -0xF9E28FFE   // 255   , hasLower (add 120), hasTitle, supradecimal 31, otherLowercase, …
  };
```

The rule read the comma in the *comment*, concluded the array was already done, and emitted nothing;
the renderer then printed the comma itself, and the verifier saw a token the source did not have.
The rule's own javadoc had called this out and dismissed it — *"such code is bizarre enough that we
accept the trade"* — which `java.base` disproves. Comments are stripped before the scan now.

**D9 is still deferred.** It asks for the comma only when the initializer prints multi-line, and the
rule still adds it unconditionally; the evidence against every implementation of it stands.

**Corpus gate at `efb8bc5`: 1,276 files, 0 unparseable, 0 refused, 0 non-reparsing, 0
non-idempotent, 14 over width.** Over-width rose by one file — `CharacterDataLatin1` itself, whose
133 long lines are all its own element comments, present in the source and left alone by v1. javafmt
introduces none of them.

### 2026-09-21 — a comment past an enum constant's separator

Fixing the refusals above made this visible: `NEW,    // the stream is new` came out as `NEW,` with
the comment on the line below, documenting the constant after the one it was written against.

javafmt already handles this for argument and parameter lists. A `//` comment cannot be printed
*before* the separator without commenting the separator out, so `CommentAttacher` routes one written
past a comma to the following element's `interior` channel, meaning "emit this after the separator",
and the list renderer reads it through `CommentDocs.listSeparator`. An enum's constants are the
third comma-separated list in the language and were not collected that way. They are now, and
`EnumRenderer` reads the channel in both of its branches.

Two things had to be got right, and `collectSeparatedList`'s javadoc says why: a comment routed to
that channel by a renderer that never asks for it is a comment the output does not contain.

- The **last** constant's separator is a `;`, not a `,`, and an enum body is full of ordinary
  declarations that each end in a `;` of their own. The terminator counts only when the previous
  element is an enum constant — a `VariableTree` whose type node has no source position — *and* the
  slot is the enum's own tail. Without the second condition, `OPEN(2); // …` followed by a field
  routed the comment to that field, whose renderer does not read `interior`, and it was dropped.
  The test caught it.
- The closing brace's break becomes hard when a comment sits on that last line, both so the enum
  cannot print flat with its `}` inside the comment and because the ordinary `Doc.LINE` would then
  be a second newline, leaving a blank line above the brace.

`java/lang/StackStreamFactory.java` now formats as its author wrote it:

```java
    NEW, // the stream is new and stack walking has not started
    OPEN, // the stream is open when it is being traversed.
    CLOSED; // the stream is closed when the stack walking is done
```

**Still as before:** an enum *with* body declarations keeps a comment past its terminating `;` as a
leading comment on the first declaration. Moving it would need the member renderers to read the
channel too, and that is a wider change than this shape earns.

**Corpus gate at `a5c3eb2`: 1,276 files, 0 unparseable, 0 refused, 0 non-reparsing, 0
non-idempotent, 14 over width.**

### 2026-09-24 — the over-width lines, **14 files → 11**

158 lines over 150 characters across 14 files. **147 of them are comments**, which v1 leaves alone
by the rule in `CLAUDE.md`, and 133 of those are `CharacterDataLatin1`'s per-element annotations,
already over width in the source. That leaves **11 lines javafmt is responsible for**, and eight
are now fixed.

| Construct | Lines | Fix |
|---|---|---|
| `case A, B, C ->` label lists | 3 | `WrappableList.joined`, comma trailing each line. `java/util/Formatter.java` had a `case` with twenty labels on a 331-character line, the widest in `java.base`. |
| Declaration annotations with arguments | 4 | `FieldRenderer` now uses the `preambleOf`/`finish` pair the method renderer already used, so an annotation carrying arguments takes its own line. |
| `catch (A \| B \| C e)` union types | 1 | The same helper with the `\|` leading each continuation line, as google-java-format writes it. |

`WrappableList.joined` is the unbracketed form of the shape D-1 introduced; what goes between two
elements is the caller's, which is the whole difference between the two uses.

Two things the tests caught that the corpus would not have:

- A `case` label list of **one** label must not get the group. A deconstruction pattern is a single
  label that wraps over several lines by itself, and the list's continuation indent landed on top of
  its own — `width-deconstruction-pattern` came out indented twice.
- `@Getter @Setter int x;` has to stay on one line. Several marker annotations inline are one
  declaration, not three lines, and "annotations are left alone" means the author's layout decides.
  The rule that earns the fix is narrower than the method one: inline stays inline while every
  annotation is argument-less, because an annotation with arguments is long and, being rendered from
  source text, has no break point inside it. `FieldRendererTest` failed the moment the method rule
  was applied wholesale.

**Three lines remain, and each has a reason.**

- `AbstractValidatingLambdaMetafactory.java:220` is a single 146-character string literal at an
  indent of 28. Breaking a literal means adding a `+` and a second literal — new tokens, which the
  output verifier reports as corruption. Nothing to do.
- `ClassSpecializer.java:913` is a declaration whose initializer already wrapped; the *head* line
  still overflows because the indent alone is 80 columns, four compounding levels of the +8
  continuation rule. Breaking after the `=` would fix it, and `renderAssignmentLike` deliberately
  does not: a value that can break internally breaks where it should, and breaking after the
  operator as well pushes the whole value down a line and one indent further. What is wanted is
  "break here only if the first line still overflows", which the Doc algebra cannot express — a
  `Group` plus `LINE` breaks whenever the whole thing does not fit flat, which is nearly always.
- `Duration.java:1576` is the interesting one: **the source fits in 150 and javafmt's output does
  not.** javac constant-folds `"a" + "b"` into one `LiteralTree`, the renderer prints its span
  verbatim, and the continuation line is re-indented from column 20 to 47. The argument list would
  have broken and left room, but `fitsForward` charges a multi-line `Verbatim` only its *first*
  line, so the call looks like it fits.

  **Attempted and reverted:** charging the widest line instead. It fixed the line — 11 files to 10 —
  and made **10 files non-idempotent**, because the literal's re-indented width feeds back into the
  fit decision that produced it and oscillates. A long line is worse than a wrong line; a
  non-idempotent formatter is worse than both. Doing this properly means measuring a relocated
  literal at the column it will land at rather than against the remaining budget, which is a change
  to the fit contract and wants its own plan item.

**Corpus gate at `b3e38e7`: 1,276 files, 0 unparseable, 0 refused, 0 non-reparsing, 0
non-idempotent, 11 over width** — 150 lines, 147 of them comments.

### 2026-09-27 — javafmt on javafmt

274 Java sources across the five JVM modules (`src/*/java`, excluding the deliberately unformatted
`test-fixtures`), through a CLI built with `--rerun-tasks` so nothing was measured against a stale
jar.

| | |
|---|---|
| `--check` | **274 files, 0 would reformat, exit 0** |
| Refused | **0** |
| `format(format(x)) != format(x)` | **0** |
| Build and tests after formatting | `gradle check` across all five modules, **1,514 tests, 0 failures** |
| Over width | 13 files, 20 lines |
| Lint warnings | 11, now **1** |

Spotless already formats this repo with javafmt, so "0 would reformat" was the expected answer and
is not the interesting part. The interesting part is what the warnings turned out to be.

**A real bug: `HideUtilityClassConstructor` over-reported, three ways.** Four of the eleven warnings
were false. `LintRewriteException` was called a utility class for carrying a `serialVersionUID`, and
three test classes for holding a single private static helper. The rule is warning-only and its
oracle test only asserts one direction — every Checkstyle violation must be matched by a javafmt
diagnostic — so over-reporting was invisible to it.

Rather than reason about what Checkstyle *should* say, I asked it, through the `CheckstyleOracle`
this module already carries, and derived the contract from eleven probes. `HideUtilityClassConstructorCheck`
reports only when a class has **no `extends` clause**, at least one **non-private** static member,
no instance members, and no private constructor **of any arity**. javafmt diverged on all three:

| javafmt said | Checkstyle says | Cost on javafmt's own source |
|---|---|---|
| a superclass is irrelevant | a class that extends something is never a utility class | `LintRewriteException` |
| any static member is static content | only a non-private one is an API worth hiding a constructor from | 3 test classes |
| the constructor must be no-arg | any private constructor hides the class | none yet |

Fixed in `c61dd9e`, with the four shapes added to the oracle corpus and the diagnostic reworded —
it no longer says "no-arg". **Warnings 11 → 7.**

**The remaining 7 are noise, and the owner should decide what to do about them.**

- Six were `empty method body`, from `NoopFormatCache` (two) and `CheckstyleOracle`'s four
  `AuditListener` callbacks. Every one a deliberate no-op. Not a parity bug — Checkstyle's
  `EmptyBlock` has no token for a method body and `EmptyBlockOracleTest`'s javadoc recorded warning
  on one as javafmt's own addition — but a warning that is 100% false on its author's codebase will
  be ignored everywhere else. **Owner decided 2026-09-27: drop it.** `EmptyBlock` now covers exactly
  Checkstyle's scope. Removed in `d7bc50f`.
- One is `switch statement does not have a 'default' case`, on `CommentAttacher`'s exhaustive arrow
  switch over `Slot.Kind`. Checkstyle reports the same thing for the same code, so parity holds and
  javafmt is not at fault; it is still a warning no reader would act on.

**The 20 over-width lines are almost all test data.** Sixteen sit inside string literals and text
blocks — formatter inputs and expected outputs, which javafmt must print verbatim and which are long
on purpose. The other four are `@ValueSource(strings = {…})` annotations, the longest 243
characters: that is D-1's remaining annotation half showing up in javafmt's own test suite, and it
is the strongest case yet for routing annotations through the Doc pipeline.

### What a user sees when javafmt refuses a file

Checked end to end, because "it is refused" is only safe if the person running it can tell.

| | |
|---|---|
| CLI `--check` | prints the diagnostic, **exit 1** — CI fails |
| CLI format in place | prints the diagnostic, **exit 1**, and the file is **byte-identical on disk** (verified by checksum) |
| Gradle/Spotless | surfaces it as a lint and **fails the build** |
| IntelliJ | an ERROR balloon, `<file> was left unformatted:` plus the diagnostic |

The message itself was the gap. It read
`error: javafmt changed more than layout and refused to write the result: tokens changed: output dropped …`
— javafmt's own vocabulary, no position, and in a build log a bare `error:` on the user's file reads
as a complaint about *their* code. It also never said the file was untouched, and the
"please file a bug" text existed only in `Main`'s uncaught-exception handler, which B2 made
unreachable for this path. **Fixed in `07cd194`:** a refusal now leads with whose fault it is, says the
file was left alone, then gives the detail and where to send it —
`javafmt bug: formatting this file changed more than its layout, so the file was left unchanged and
nothing was written — tokens changed: … . Your code is fine; please report this at
https://github.com/javafmt-io/javafmt/issues`. Two `JavafmtVerifyOutputTest` cases pin those three
facts. All three clients inherit it from core.

Still imperfect: a refusal carries no line:col, because the multiset comparison cannot localise a
difference — that is the A6 deferral showing through. Spotless additionally prefixes its own
`LINE_UNDEFINED javafmt(java.lang.IllegalArgumentException)`, which is noise from the step throwing
a generic exception type.

### Follow-up items filed from this run

- **B8** — `final` dropped from a type pattern's binding variable. Found here, fixed here.
- **D-3 tightening** — `if(c){s;}` came back from Spotless with the author's missing spaces intact.
  The preserved one-line form now has to be spaced the way javafmt would space it.
- Two open items recorded against **A6** and **D-5** (see those entries): the verifier still compares
  token multisets rather than sequences, and an array initializer still gets an unconditional
  trailing comma.
