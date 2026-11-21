# Codebase Simplification and Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the real defects and weak tests that ten read-only reviews found across every javafmt module, then land their behavior-preserving simplifications, one topic per commit.

**Architecture:** Defects first, each pinned by a failing test. Vacuous tests next, so later refactors have a net. Then per-module refactors, docs corrections and build-logic cleanups. A second review round covers the parts of `javafmt-core` the first round skipped.

**Tech Stack:** Java 25 (Java 17 for the Gradle plugin's main classes), Gradle 9.9.2, JUnit 5, AssertJ, Lombok, NullAway/Error Prone, TypeScript/Jest (VS Code), IntelliJ Platform 2026.2, Docusaurus.

**Spec:** none written. The input is the ten review reports of 2026-11-20, one per project (core, daemon, cli, spotless, intellij, maven-plugin, gradle-plugin, vscode, website+docs, build logic). Each task below names the file and line the report cited. Line numbers were read at HEAD `8da895c` and may have drifted. Every finding is the reviewing agent's reading of the code. Nothing was run, so **Step 1 of every defect task is to reproduce the claim with a failing test; if the test passes, stop and drop the task.**

## Global Constraints

- Build with `~/.sdkman/candidates/gradle/9.9.2/bin/gradle` (sdkman `current` stays 9.6.1). Never `./gradlew`, never `npm`; TypeScript goes through `gradle :javafmt-vscode:npmBuild` / `npmTest`.
- TDD is mandatory: a failing test first, then the minimum code, then refactor. Tests are never skipped or disabled.
- Tests use AssertJ only; prefer `@ParameterizedTest` for repeated assertion logic.
- Java style: `final var`, `final` parameters, records and sealed interfaces, immutability, streams over mutable accumulators, no nulls (`@NullMarked`, NullAway), no checked exceptions in public API, no comments in new code.
- `javafmt-core` has no runtime dependencies. The Gradle plugin's main classes stay on Java 17 (no `_`, pattern switch or `getFirst()`).
- Commits are unsigned (`git -c commit.gpgsign=false commit`), single-line `type: description`, no body, no trailers, never pushed. One commit per topic; fold follow-ups into the topic's commit as `fixup!`. Never amend or rebase without the owner's OK.
- Run `gradle spotlessApply` before each commit; `docs/rules.md` changes in the same commit as any change to formatter output.
- Every javac lint, Error Prone finding and Javadoc warning fails the build (`-Werror`).

## Review Focus

Failure modes the reports imply that no existing test exercises, most likely first:

1. A Java 17 or 21 project formatted by the CLI, Maven plugin, Gradle plugin or daemon gets `_` renames that do not compile there. Expected: no `_` below release 22. (Task 1)
2. A `Stream.<A>of(Foo.<B>bar())` statement with several type witnesses. Expected: output still compiles. (Task 2)
3. Non-UTF-8 bytes on stdin and non-ASCII output under `LANG=C`. Expected: exit 2 with a clear message; UTF-8 out regardless of platform charset. (Task 3)
4. The same checkout copied to two paths. Expected: equal Spotless step keys, so the build cache is reusable. (Task 4)
5. A `.javafmt` reached through a symlink. Expected: editing it re-keys the step. (Task 4)

Each line's test is in the owning task.

## Open Decisions (owner answers before the named task starts)

| # | Decision | Blocks |
|---|---|---|
| D1 | Pass the project's target release through every integration, or document "every integration assumes release 25"? Plan assumes pass it through. | Task 1 |
| D2 | `""` classpath in `JavafmtFormatterStep`: parse-only or JDK-only attribution? Main treated it as parse-only. | Task 4 |
| D3 | Delete public `JavafmtFormatterStep.apply(String)` and `apply(String, JavafmtConfig)` (published API)? | Task 17 |
| D4 | `--check` with a parse error on stdin: print source or stay silent? Javadoc says silent. | Task 3 |
| D5 | `--daemon` with `--cache`, `--exclude`, `--classpath`, `--ratchet-from`, `--diagnostics`: reject with a usage error? Invalid `--exclude` glob: usage error instead of "internal error"? | Task 3 |
| D6 | Maven check goal: log warnings like the format goal? Relative `cacheFile`: resolve per module? Goal descriptions given the no-comments rule? | Task 19 |
| D7 | Switching the IDE engine away from NATIVE: stop running daemons? | Task 20 |
| D8 | VS Code behavior: clear diagnostics on close, accurate restart message, drop the double "javafmt:" prefix? | Task 21 |
| D9 | NullAway scope for test-like source sets: really off everywhere, per CLAUDE.md? | Task 27 |
| D10 | Version-stability promise: "pre-1.0 any release may change output"? | Task 23 |

---

## Phase 1: Defects (each starts from a failing test)

### Task 1: Pass the target release through every integration

**Files:**
- Modify: `javafmt-cli/src/main/java/io/javafmt/cli/FormatRun.java:309`, `javafmt-daemon/src/main/java/io/javafmt/daemon/DaemonHandler.java:27`, `javafmt-gradle-plugin/src/engine/java/io/javafmt/gradle/engine/JavafmtEngine.java:34`, `javafmt-maven-plugin/src/main/java/io/javafmt/maven/AbstractJavafmtMojo.java:96,151`, `docs/rules.md:403`
- Test: matching test classes in each module (`CliTest`, `DaemonHandlerTest`, `JavafmtPluginFunctionalTest`, `JavafmtMojoTest`)

**Interfaces:**
- Consumes: `JavafmtConfig.defaults().withRelease(int)` (core, exists).
- Produces: each integration reads its host's target release (CLI: new `--release <n>` option; daemon: `FormatRequest.release` field in `daemon.proto`; Gradle: `compileJava` `options.release`; Maven: `maven.compiler.release`) and passes it to the config. Absent means 25.

- [ ] **Step 1: Failing test per integration.** Format `var`-eligible code with an unused catch parameter, at release 17, and assert the output contains no `_`. Names: `aRelease17ProjectGetsNoUnnamedVariables` in each class. Run each; expect FAIL. If the CLI or daemon has no release input, the test drives the new option or field.
- [ ] **Step 2: Add the CLI option and the daemon field** (`--release`, `FormatRequest.release`; zero means 25, and the VS Code and IntelliJ clients leave it unset).
- [ ] **Step 3: Read the release** in the Gradle engine from the task's `release` input (set from `compileJava`) and in the Maven mojo from `maven.compiler.release`, falling back to `maven.compiler.target`, then 25. Keep `CacheKey` keyed on the release (Maven's `CONFIG` becomes a per-execution value).
- [ ] **Step 4: Run** `gradle :javafmt-cli:test :javafmt-daemon:test :javafmt-gradle-plugin:functionalTest :javafmt-maven-plugin:test` — expect PASS.
- [ ] **Step 5: Update `docs/rules.md:403`** to say which setting each integration reads, then `gradle spotlessApply` and commit `fix: format at the project's target release in the CLI, daemon, Gradle and Maven integrations`.

If D1 answers "document only": skip Steps 1-4 and make the doc line say "every integration assumes release 25".

### Task 2: `RedundantTypeArguments` keeps a witness javac needs

**Files:**
- Modify: `javafmt-core/src/main/java/io/javafmt/lint/RedundantTypeArguments.java` (`dropRedundantWitnesses` ~260-287, `tryEmitDrop` ~300-321)
- Test: `javafmt-core/src/test/java/io/javafmt/lint/RedundantTypeArgumentsAnalyzeTest.java`, fixture `lint-redundant-type-arguments-witness-needed`

**Interfaces:**
- Produces: nothing new. The rule must keep `<T>` when both arguments are implicitly typed lambdas and inference falls back to `Object`.

- [ ] **Step 1: Failing test** `aWitnessThatPinsAnOtherwiseUninferredTypeIsKept`: source `RuleScan.<MethodTree>collecting(m -> a(m), m -> b(m))` with a signature `static <T> RuleScan collecting(Function<List<T>, X>, Function<List<T>, Y>)` on the attributed classpath. Assert the witness survives. Add `twoWitnessesInOneStatementAreEachVerified` for `Stream.<A>of(Foo.<B>bar())`. Expect FAIL on the first.
- [ ] **Step 2: Run** `gradle :javafmt-core:test --tests '*RedundantTypeArguments*'` — expect FAIL.
- [ ] **Step 3: Verify each drop by recompiling** the rewritten statement against the batch (the rule already verifies against an attribution batch for other rewrites; reuse that path in `tryEmitDrop` and drop the witness only if attribution still succeeds with the same types).
- [ ] **Step 4: Run the rule's tests, fixtures and `LintCompileSafetyTest`** — expect PASS.
- [ ] **Step 5: Commit** `fix: keep a type witness whose removal changes inference`.

### Task 3: CLI input and output encoding, and `--check`/`--daemon` usage errors

**Files:**
- Modify: `javafmt-cli/src/main/java/io/javafmt/cli/Cli.java:40-43`, `FormatRun.java:94,97-120`, `FormatCommand.java:165-170`
- Test: `javafmt-cli/src/test/java/io/javafmt/cli/CliTest.java`

**Interfaces:**
- Produces: `Cli.run` wraps `out` and `err` in UTF-8 `PrintStream`s once and hands those to picocli and `FormatCommand`.

- [ ] **Step 1: Failing tests.** (a) `invalidUtf8OnStdinIsRejected`: stdin bytes `{0xff, 0xfe, 'a'}` expect exit 2, empty stdout, stderr `javafmt: <stdin>: read failed: not valid UTF-8`. (b) `outputIsUtf8WhateverThePlatformCharset`: pass `new PrintStream(buf, true, ISO_8859_1)` as out, stdin `class A { String s = "é"; }`, assert stdout bytes equal the UTF-8 encoding. Expect both FAIL.
- [ ] **Step 2: Decode stdin strictly** with `UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()` inside the existing `catch IOException`.
- [ ] **Step 3: Wrap `out`/`err` once in `Cli.run`**; confirm daemon mode, which writes bytes, is unaffected and flushes.
- [ ] **Step 4: Per D4 and D5,** add tests then behavior: `runStdin("class {", "--check")` pins stdout; `--exclude '[a'` gives a usage error; each daemon-ignored flag gives a usage error.
- [ ] **Step 5: Run** `gradle :javafmt-cli:test` — expect PASS. Commit `fix: decode stdin strictly, write UTF-8 output and reject unusable option combinations`.

### Task 4: Spotless step key must be location-independent

**Files:**
- Modify: `javafmt-spotless/src/main/java/io/javafmt/spotless/ProjectFiles.java:34-43,63-65,73,78`, `JavafmtFormatterStep.java:45,72`
- Test: new `ProjectFilesTest.java`; `JavafmtFormatterStepTest.java`

**Interfaces:**
- Produces: `ProjectFiles.under(Path)` returns a value whose equality uses root-relative paths and, per `.javafmt`, relative path plus content. The root's own `.git` is not recorded.

- [ ] **Step 1: Failing tests.** `theKeyIsTheSameInTwoCheckouts`: copy a tree (`.git` dir, nested `.javafmt`) into two temp dirs and assert `create(a).equals(create(b))`. `aSymlinkedProjectFileIsTracked`: change the symlink target's content and assert the step is no longer equal. Expect both FAIL.
- [ ] **Step 2: Fix** by skipping the root's `.git` the way root files are skipped, storing relative paths, and using `Files.isRegularFile(file)` (follows links) at the file visit.
- [ ] **Step 3: Per D2,** pin the meaning of `""` with a test for each entry point, then route both through one private `withClasspath(JavafmtConfig, String)`.
- [ ] **Step 4: Add the two characterization tests** that should already pass: a `.javafmt` above the project directory keys the step; removing a nested `.javafmt` re-keys it.
- [ ] **Step 5: Run** `gradle :javafmt-spotless:test :javafmt-spotless:integrationTest` — expect PASS. Commit `fix: key the Spotless step by project-relative content so checkouts share a build cache`.

### Task 5: `FileFormatCache.save` leaks its temp file

**Files:**
- Modify: `javafmt-core/src/main/java/io/javafmt/cache/FileFormatCache.java:124-153`
- Test: `FileFormatCacheTest.java`

- [ ] **Step 1: Failing test** `aFailedSaveLeavesNoTempFile`: make the target directory read-only after the temp file is created (or inject a failing move), assert no `*.tmp` remains. Expect FAIL.
- [ ] **Step 2: Reuse `SourceFiles.write`** (or extract its move helper into a shared package-private method) so the temp file is deleted on failure.
- [ ] **Step 3: Run** `gradle :javafmt-core:test --tests '*FileFormatCache*'` — expect PASS. Commit `fix: delete the cache's temp file when a save fails`.

### Task 6: Release and Pages pipelines

**Files:**
- Modify: `javafmt-maven-plugin/build.gradle.kts:12-21,36-82`, `.github/workflows/release.yml:58-59`, `.github/workflows/pages.yml:5-7`, `.github/actions/setup-build/action.yml`
- Test: Gradle task output (below)

- [ ] **Step 1: Reproduce.** `gradle :javafmt-maven-plugin:tasks --all | rg Publication` (expect nothing today); `gradle build --dry-run | rg npmTestIntegration` (expect a match, and the release job has no setup-node or `xvfb-run`).
- [ ] **Step 2: Maven plugin publication.** Apply `javafmt.publish-conventions`, set `pom.packaging = "maven-plugin"` on `mavenJava`, delete the duplicated POM/signing/`afterEvaluate` block. Verify with `gradle :javafmt-maven-plugin:publishAllPublicationsToStagingRepository` and check `build/staging-deploy/io/javafmt/javafmt-maven-plugin/` holds the pom, sources, javadoc, `.asc` files and a jar containing `META-INF/maven/plugin.xml`. Commit `build: publish the Maven plugin through the shared publish conventions`.
- [ ] **Step 3: Release job.** Add a `node` input to `setup-build` (one pinned source, recorded in CLAUDE.md), and either run `gradle assemble :javafmt-intellij:buildPlugin` or add setup-node and `xvfb-run -a` as `build.yml` does. Run `actionlint`. Commit `ci: give the release job the Node and display setup its build needs`.
- [ ] **Step 4: Pages.** Add `'docs/**'` to the `paths` filter. Commit `ci: redeploy the site when the docs change`.

---

## Phase 2: Tests that cannot fail

### Task 7: IntelliJ test hygiene

**Files:**
- Modify: `javafmt-intellij/build.gradle.kts:157`, `FakeDaemon.java:75`, `JavafmtFormattingServiceTest.java:240,253,281,460`, `JavafmtBinaryTest.java`
- Test: the suite itself

- [ ] **Step 1: Failing run.** `gradle :javafmt-intellij:test -Pversion=0.3.0` — expect "too old" failures from the `javafmt 0.2.0` fakes.
- [ ] **Step 2: Replace each hard-coded version with one shared constant `"javafmt 99.0.0"`** and swap `Thread.sleep(1_100)` at `:253` for `Files.setLastModifiedTime`. Re-run with `-Pversion=0.3.0` and without — expect PASS.
- [ ] **Step 3: Confirm the vintage engine id,** then `useJUnitPlatform { excludeEngines("junit-vintage") }`. Expect the report total to fall from 192 to 163 with no failures and every method still listed once.
- [ ] **Step 4: Commit** `test: stop the IntelliJ fakes depending on the build version and run each fixture test once`.

### Task 8: Maven plugin tests that prove nothing

**Files:**
- Modify: `javafmt-maven-plugin/src/test/java/.../JavafmtMojoTest.java`, `JavafmtCheckMojoTest.java`, `MojoTestSupport.java`

**Interfaces:**
- Produces: `MojoTestSupport.formatMojo(Path)`, `checkMojo(Path)`, a capturing `Log` (subclass of `SystemStreamLog`), constants `FORMATTED`, `ORDER`, `EDITED_INVOICE`; `buildProject` takes an explicit test directory and sets `build.setDirectory`.

- [ ] **Step 1: Write the missing tests,** expecting each to pass or fail as noted: `formatGoalAlsoRewritesTestSources` (FAIL until the test directory exists), `theSummaryLineCountsChangedFiles` (`javafmt: formatted 1 files, 0 warnings`), `aSecondCheckOnTheSameCacheStillFails` (guards against recording an unformatted file as up to date), `anUnwritableFileFailsWithTheIoCause` (skip when root), `aCacheEntrySkipsTheSecondRun` asserting `target/javafmt.cache`.
- [ ] **Step 2: Make the two vacuous cache tests real** per the report (check `target/javafmt.cache`, not `srcDir`; prove skipping via the logged warning appearing only with the cache off).
- [ ] **Step 3: Run** `gradle :javafmt-maven-plugin:test`; then mutate (disable the cache write) to confirm the cache tests fail. Revert the mutation.
- [ ] **Step 4: Commit** `test: make the Maven plugin's cache, logging and test-source tests prove what they claim`.

### Task 9: VS Code tests: synchronous sends and type-checking

**Files:**
- Modify: `javafmt-vscode/src/test/unit/extension.test.ts:99-128,217-220,357-409`, `src/__mocks__/child_process.js`, `tsconfig.json`; create `tsconfig.test.json`, `src/test/unit/wire.ts`

**Interfaces:**
- Produces: `nextRequest(proc): Promise<FormatRequest>` resolving when stdin is written; `sentRequestId` throws when nothing was sent; one mock-process factory with `PassThrough` streams; `decodeRequest` and `decodeAll(chunks: Uint8Array[])` in `wire.ts`.

- [ ] **Step 1: Make the three tests await `nextRequest`** and use `jest.advanceTimersByTimeAsync`. Run `gradle :javafmt-vscode:npmTest` — expect PASS before and after.
- [ ] **Step 2: Add `tsconfig.test.json`** (extends base; includes tests and mocks; `types: ["node","jest"]`, `noEmit`) and an npm script run by the `npmTest` Gradle task. Expect type errors; fix them.
- [ ] **Step 3: Collapse the two mock factories,** make the outer `beforeEach` independent of PATH (`onlyExecutable(() => false)`), parameterize the severity/position/statusFor tests, replace the Windows early-return with an honest `it.skip`-on-win32 or an injected platform.
- [ ] **Step 4: Run** `gradle :javafmt-vscode:npmTest` — expect PASS. Commit `test: type-check the VS Code unit tests and stop three of them depending on a synchronous send`.

### Task 10: Core and daemon test gaps

**Files:**
- Modify: `javafmt-daemon/src/test/java/.../DaemonHandlerTest.java`, `DaemonTest.java`, `WireTest.java`; `javafmt-core/src/test/java/io/javafmt/parser/RoundTripVerifierTest.java:121,127`; lint tests; create `javafmt-core/src/test/java/io/javafmt/lint/LintFixtures.java`

**Interfaces:**
- Produces: `LintFixtures.fix(LintRule rule, String source): String`; `DaemonHandler.diagnostic(io.javafmt.Diagnostic)` package-private; `DaemonFixtures.request(int, String)`.

- [ ] **Step 1: Daemon.** Add a `@ParameterizedTest` that builds each core diagnostic record (`new Position.At(3, 5, 0)`) and asserts Kind, Severity, line and column, including `KIND_LINT` and `KIND_INTERNAL`. Add `aRequestWithoutASourceIsRejectedByTheHandler` (expect FAIL today). Add `aZeroThreadCountIsRejected`.
- [ ] **Step 2: Core.** Add `RoundTripVerifierTest` cases for the exact offset of a changed token, a longer input, and a longer output (must be empty). Replace the ~14 copies of the lint `fix()` helper with `LintFixtures.fix`, and make count-only tests (`RedundantModifierTest` 10, `NeedBracesTest` 7, `ExplicitInitializationTest` 7, `ModifierOrderTest` 6) assert the fixed source.
- [ ] **Step 3: Run** `gradle :javafmt-daemon:test :javafmt-core:test` — expect PASS (new tests that expose a bug are triaged into their own task).
- [ ] **Step 4: Commit** `test: pin daemon diagnostic mapping and round-trip offsets, and assert fixed source in lint tests`.

---

## Phase 3: Behavior-preserving refactors, per module

Each task: confirm the named existing tests are green first, make the change, re-run, `gradle spotlessApply`, commit. A change that alters output needs a fixture and a `docs/rules.md` update in the same commit.

### Task 11: Core builder and parser dead code
- [ ] Make `Recursor.scan` non-null throughout: delete the 13 `Optional.ofNullable(recursor.scan(..))` wrappers, `Recursor.scanNullable`, `DocBuilder.scan(@Nullable)`, the `@Nullable` on `MethodRenderer.render` and `BodyMemberRenderer.render`; `MethodRenderer` throws `IllegalStateException` for `<init>`. **Test first:** `DocBuilderTest` asserts dispatching a constructor `MethodTree` throws ISE. Commit `refactor: make the builder's scan non-null`.
- [ ] Remove `DocBuilder`'s unused `config` parameter: `DocBuilder.build(unit): BuildResult`; update `FormatStages.java:86`, `DocBuilderBenchmark.java:67` and the diagnostics tests. Commit `refactor: drop the unused config parameter from DocBuilder`.
- [ ] Delete `JavaParser.HAS_JAVAC_INTERNAL_ACCESS`, `computeHasJavacInternalAccess` and the one test reading it. Commit `refactor: delete the unused javac-access constant`.
- [ ] Share the literal and comment skippers between `TokenScanner` and `CommentScanner` (make `CommentScanner`'s package-private or move to a `Literals` class). Guard: `TokenScannerTest`, `CommentScannerTest`, `RoundTripVerifierTest`. Commit `refactor: share the literal and comment skippers between the two scanners`.

### Task 12: `RedundantTypeArguments` without accumulators (after Task 2)
- [ ] `scan` returns `record Found(List<LintEdit> diamonds, List<WitnessCall> witnessCalls)`; `tryEmitDrop` returns `Optional<LintEdit>`; `WitnessCall.deletion(): LintEdit`; `stripAllWitnesses` uses `LintEngine.applyEdits`. Guard: Task 2's tests, `RedundantTypeArgumentsAnalyzeTest`, the seven `lint-redundant-type-arguments*` fixtures, `LintCompileSafetyTest`. Commit `refactor: build RedundantTypeArguments results with streams`.

### Task 13: Core duplication
- [ ] One `TypeRenderers.typeArguments(List<? extends Tree>, Recursor)` replacing four renderings; **test first:** a fixture covering `Foo::<String>bar`, `new <T>Foo()`, `Stream.<T>of()`.
- [ ] One static `TreeFacts.isEnumConstant(VariableTree, CompilationUnitTree, SourcePositions)` replacing three copies.
- [ ] `ChainScanner.visitCases(Tree, List<? extends CaseTree>, P)` hook replacing the paired switch overrides in `CommentAttacher`, `DefaultComesLast`, `FallThrough`; **test first:** a `ChainScannerTest` case.
- [ ] `RoundTripVerifier`: extract `OptionalInt divergence(List<Token>, List<Token>)`, import `Collectors`, use `Collectors.joining("\n")` in `normalizeComment`.
- [ ] `Recursor.facts()` replacing 12 pass-through methods and 25 `DocBuilder` overrides.
- [ ] One commit per bullet.

### Task 14: Core lint and batch clean-ups
- [ ] `VarRetypingCheck`: `Scope.contains(long)`; move `withoutParentheses` to `Declarations`.
- [ ] `LocalVarUseVar`: list-returning `replaceTypeEdit`, flatten `unprovenEdits`, one `partitioningBy`.
- [ ] `CommentAttacher`: `append(channel, owner, c)`, `Optional` from `firstEnclosing`, `record Span(long start, long end)`, sort members once, `List<? extends Tree>` parameter.
- [ ] `BatchFormatter`: `Gatherers.windowFixed`, `Collections.nCopies`, delete `fromAttributed` (move its single test use to a helper).
- [ ] Guards: each class's tests plus `CommentAuditTest`, `JavafmtFormatAllTest`, `BoundedMemoryTest`, fixtures. One commit per bullet.

### Task 15: Daemon
- [ ] Delete the two test-only `Daemon.serve` overloads (tests pass `DEFAULT_THREADS`).
- [ ] Per-call `Connection(wire, out, handler, fatal)` replacing `Replies` and the threaded arguments; **medium risk, guard:** `noReplyIsWrittenWhileTheFatalHandlerRuns`, `everyConcurrentRequestGetsExactlyOneWholeReply`, `noMoreRequestsFormatAtOnceThanTheDaemonHasThreads`, `theNextRequestIsReadWhileTheCurrentOneIsStillBeingFormatted`.
- [ ] `Wire.decode` wraps in `UncheckedIOException` (**test first:** change `aJsonLineThatIsNotARequestIsRejected` to expect it); move the source check into `DaemonHandler.handle`.
- [ ] Test and build tidying (items 6, 8-11, 13-21 of the report): `Collectors.joining()` in `DaemonFixtures`, hoisted path in `build.gradle.kts`, helper placement, import `Diagnostic`.

### Task 16: CLI
- [ ] `DiagnosticsFormat` as its own file replacing the `boolean jsonDiagnostics` threaded through six methods (leave the three text-only wording differences explicit). **Test first:** a cache-save-failure test.
- [ ] `Expansion` holds `List<Path>`; apply exclude and ratchet filters once; `finalizeFormatOutcome` takes the `Formatted` and uses `changed()`.
- [ ] `ExitCode` utility class; drop `Version` for `@Command(version = "javafmt " + JavafmtVersion.VERSION)`; delete the 5-argument `Cli.run` and use one test helper; `Arguments` validates `threads > 0` and drops `Math.max`.
- [ ] `JsonDiagnosticsTest`: parameterize, add `ConfigError`, `\r`, `\b`, `\f`, non-BMP and backslash paths; **then** tidy `JsonDiagnostics.escape` to `codePoints()` streaming with `HexFormat`.
- [ ] Native: add `--classpath ""` to `tracingAgentCapture` and a native test that expands a star import; share one argument list between `nativeCompile` and the Docker variant (or delete the Docker variant, Task 27).

### Task 17: Spotless refactors
- [ ] `FormatterStep.create(NAME, new Setup(...), Setup::state, State::formatter)`; reflective `apply` delegates to `Func`; `throwOnError` private (**test first:** strengthen the invalid-project-file assertion to the full message); reorder parameters to `(formatterVersion, config, projectDirectory)`.
- [ ] Per D3, delete the two public `apply` overloads and re-point tests at `Fixtures.FORMATTED_FOO`.
- [ ] `Finder.result()` and `Path.endsWith` in `ProjectFiles`; a `ProjectFilesTest` asserting exact `below` and `repositoryRoots`.
- [ ] Test tidy: merge the four keying tests into one `@ParameterizedTest`, drop 13 redundant `@TempDir`s, constants for repeated literals, a control case for the "witness kept" test. Build: catalog alias for `spotless-plugin-gradle`, drop `useJUnitPlatform()`, test whether the `slf4j.simple` dependencies are still needed.

### Task 18: Gradle plugin (unit tests first)
- [ ] Add `javafmt-gradle-plugin/src/test/java` covering `EngineJar` (extract once, reuse, concurrent extraction), `ProjectRelativeExcludes` (empty patterns exclude nothing, `../shared/gen/**` matches), `upToTheRepositoryRoot` (`.git` dir and `.git` file stop the walk), `JavafmtEngine` (changed, errors, warnings with line and column).
- [ ] Then, each with its named guard: stream `findings` without the `Function`; extract `request`/`reportWarnings`/`failure`/`writeMarker` from `JavafmtAction.execute`; drop `Parameters.getAnalyze()`; delete `Summary` for `Outcome`; `project.absoluteProjectPath(FORMAT)` (**test first:** assert `gradle :app:javafmtFormat` in the failure text); `RatchetFromSet` as a record; `Stream.iterate` for the repository-root walk.
- [ ] Functional-test tidy: merge the two problems-report tests, delete `jvmArgsReachTheWorkerThatFormats`, parameterize the three classpath tests, `tasks.named` for the NullAway exemption, drop the redundant `excludes` convention.
- [ ] Held for decision: moving the ratchet rules into `JavafmtCheck`; `@ServiceReference` for the worker slot; caching the exclude spec (semver).

### Task 19: Maven plugin (after Task 8)
- [ ] `JavafmtMojo`: `forEach(write)` then count `Formatted::changed` (never rely on `filter().count()` for side effects). `JavafmtCheckMojo`: record cache hits separately from the unformatted list.
- [ ] `sourceFiles(Build)` replacing `walkNullable`; `Optional` ratchet; `failOnUnreadable` as a loop (**test first:** `hasCauseInstanceOf(CharacterCodingException.class)`); delete the two 3-argument constructors; `abstract sealed ... permits`; rewrite `@Parameter` Javadoc as plain text and confirm `rg '\{@' build/mavenPlugin/descriptor` finds nothing.
- [ ] Per D6, decide check-goal warnings, relative `cacheFile`, goal descriptions. Separate commits for the medium-risk items: `defaultValue` for `cacheFile`, removing `compileOnly(libs.slf4j.api)`.

### Task 20: IntelliJ
- [ ] **Interrupt leak (test first):** from the test package call `createFormattingTask` with a fake `AsyncFormattingRequest` and a blocking fake daemon, cancel from another thread, assert the thread is not interrupted after `run()`. Then flag-and-clear under the lock; `Optional<Thread>`.
- [ ] `volatile Optional<Outcome> stopped` plus `synchronized stop(outcome)` replacing `compareAndSet` on `Optional`.
- [ ] Single `request.onTextReady(switch ...)` via `textFor(...)`; `enum Verdict { SUPPORTED, TOO_OLD, UNKNOWN }` for `VersionGate`; one sealed lookup result for binary resolution (one provider test per case); top-level `Outcome` and `VersionProbe`; static `JavafmtBinary.version(Path)`; `JavafmtVersion.VERSION` replacing the duplicate generated class (**test first:** `JavafmtBinaryTest.theGateAcceptsABinaryReportingThePluginsOwnBuildVersion`); delete the PATH fallback and `Toggle`.
- [ ] Per D7, stop daemons on engine switch (**test first:** apply IN_PROCESS and assert the process exits via `awaitExit(pid)`). Raise the per-format timeout above the 10 s `--version` constant.

### Task 21: VS Code (after Task 9)
- [ ] `stopDaemon(reason)` and `daemonGone(proc, reason)` replacing two copies of reject-all; type `daemon` as `ChildProcessByStdio<Writable, Readable, null> | null`, read it after the classpath `await`; `stdio: ['pipe','pipe','ignore']` (**test first:** assert the spawn options); `resolveDaemonBinary` returning a union instead of throwing; extract `readReplies`; one `subscriptions.push`; shared bundled-directory helper; `messageOf`; `DaemonState` type; merge the `daemon_pb` imports; strip the `// Exported for testing.` comments.
- [ ] **Test first:** `formats the whole document for a range request`.
- [ ] Session object replacing 11 module-level variables, last, after the above.
- [ ] Per D8, the five behavior items, each with a failing test first.

### Task 22: Core remainder (second review round)
- [ ] Review the ~95 main files round one skipped (other renderers, `Printer`, `Pairs`, `MemberOrder`, `Javafmt`, `JavafmtConfig`, `PathFilter`, `AttributionSession`, `FileManagerPool`, `CacheKey`, `src/jmh`) with several fresh read-only agents split by package, then append their findings to this plan as new tasks.

---

## Phase 4: Documentation

### Task 23: Make `docs/` and the site true
**Files:** `docs/rules.md`, `docs/intro.md`, `README.md`, `website/src/pages/index.md`, `website/docusaurus.config.ts`

- [ ] **Step 1:** Add a CI-checkable guard first: set `onBrokenMarkdownLinks: 'throw'` and `onBrokenAnchors: 'throw'`; turn the plain-text "see X" references in `rules.md` into links; run `gradle :website:docusaurusBuild` and fix what throws.
- [ ] **Step 2:** Correct each claim after reading the fixture that shows the truth: switch layout (`switch-colon-form-preserved`, `switch-mixed-form`, `switch-long-arm`), `implements-long` and `type-parameters-long` layouts, IDE directives (`annotation-comments-inside-argument-list`), `UnusedImports` vs `RemoveUnusedImports`, the duplicated `&&`/`||` rule, "fat JAR" (four places) to native binaries, README's IntelliJ description, import-groups wording, the new-syntax overclaim, Kotlin DSL quotes at `intro.md:51`, Spotless 8.11.0, `.git` in the skipped-directories list, `javafmt.enable`, Maven's missing classpath and undocumented parameters.
- [ ] **Step 3:** Per D10, one version-stability statement in `intro.md`; point `rules.md:8` at it.
- [ ] **Step 4:** Site config: load `properties` highlighting (verify `rg 'attr-name'` in built HTML), delete the sidebar copy, dead `tagline`/`i18n`/`organizationName`/`CNAME`, unused packages (regenerate the lock through `gradle :website:npmInstall`), and fix the typecheck/build output overlap with `--out-dir build/site` (verify a second `gradle :website:build` shows both tasks UP-TO-DATE).
- [ ] **Step 5:** Commit per topic: `docs: ...`, `build: ...`.

---

## Phase 5: Build logic

Each item is verified by the Gradle command shown; none changes formatter output.

### Task 24: Mechanical build and CI fixes
- [ ] `build.yml:38` cache key from `package-lock.json`; `perf.yml` dead steps; `release.yml` job-level env for checksums and JetBrains secrets, one `currentVersion` call, `.sdkmanrc` `tr -d '\r'`, `SOURCE_DATE_EPOCH` opt-in on `setup-build`.
- [ ] `libs.versions.toml`: remove `opentest4j` and unused plugin aliases, give `junit4` a `[versions]` entry.
- [ ] `tasks.named { it.startsWith("spotless") }`, drop redundant imports; `JavafmtFormatter.kt` `String` field, `HexFormat` digest; fix the stale comments in `spotless-conventions`, `npm-conventions`, `native.yml`.
- [ ] Verify: `gradle spotlessCheck build`.

### Task 25: Test-property inputs
- [ ] One buildSrc `CommandLineArgumentProvider` pair (`@InputFiles @PathSensitive(NONE)` file; `@Classpath` classpath) replacing absolute-path system properties in `javafmt-core:71`, `javafmt-intellij:163-165`, `javafmt-spotless:60-63`; delete the CLI's local class. Verify: run the three test tasks, then again from a second worktree and expect FROM-CACHE.

### Task 26: Spotless convention catch-all
- [ ] **Test first:** break dependency resolution (offline or a bad coordinate) and show `spotlessCheck` currently succeeds with the classpath rules silently off. Remove or narrow the `catch (_: Exception)` in `spotless-conventions:65-72`, re-run `gradle spotlessCheck` on every module.

### Task 27: Structural build changes (one decision or one measured diff each)
- [ ] Per D9, one NullAway rule in `java-conventions` keyed on the source-set name; verify with `gradle compileTestFixturesJava compileCorpusTestJava compileOracleTestJava compileIntegrationTestJava compileFunctionalTestJava compileNativeDaemonTestJava`.
- [ ] `jvm-test-suite` for CLI, Spotless and Gradle-plugin source sets; compare test counts before and after.
- [ ] Delete the dead `nativeCompileLinuxDocker` task and `NativeBinary`, fold the remaining native conventions into `javafmt-cli`, share one GraalVM launcher spec; update `CLAUDE.md`.
- [ ] `javafmt.java-base-conventions` shared with `javafmt-intellij`; remove the slf4j constraint and `dependencyConstraints.clear()` (diff runtime classpaths and core's `module.json`); wire `build-settings` tests; `npm-conventions` reduced to the shared node setup used by `website`; drop `requestedTarget()`; single Java-version source; `tracingAgentCapture` configuration-cache fix; jmh and `profile-hour.sh` robustness (`ITERATIONS >= 1`, derived param count, macOS guard, `brew --prefix` for the async-profiler path).

---

## Self-Review

- **Coverage:** every report's defects map to Tasks 1-7, 10 or the held decisions. Report items not named here are mechanical and fall under the nearest task's bullets. **Gap:** `javafmt-core` main code beyond the ~25 files read is Task 22.
- **Placeholders:** Phase 3 and 5 tasks list changes and guards but not signatures for every helper; where a signature matters to a neighbour (Tasks 1, 8, 9, 10, 20, 21) it is in that task's Interfaces block.
- **Type consistency:** `LintFixtures.fix`, `Found`/`WitnessCall.deletion`, `DiagnosticsFormat`, `Verdict` are each defined once.
- **Unverified:** the Maven-publication, release-job and release-25 claims were spot-checked at HEAD and held; every other claim is a reviewer's reading.
