# Execution prompt: make javafmt trustworthy enough to run on other projects

You are working in the `javafmt` repository on branch `feature/javafmt-improvements`. Your job is to
execute `PLAN.md`: every Tier A, B, C, D, E and F item ticked or explicitly deferred by the owner,
ending with the corpus gate green and a dogfood run on a real project that compiles, passes its
tests, and formats identically from the CLI, from Gradle via Spotless, and from IntelliJ.
Publishing, marketplaces, docs and CI polish are out of scope; that backlog sits in
`review/release-backlog.md` and you do not touch it unless an item there blocks one in `PLAN.md`.

Read this file, then `PLAN.md`, then `CLAUDE.md`, before touching anything. `PLAN.md` is *what*;
this file is *how*. The reports under `review/` hold the reproduction for every item (file, line,
input, observed output). Read the cited report section before starting an item; the plan entry is a
summary.

## Ground rules (these override your defaults)

1. **Build commands.** Always `gradle`, never `./gradlew`, never `npm`. The Gradle daemon must run
   on JDK 25 (check `java -version`); on a JDK 21 daemon every `spotlessJavaCheck` fails with
   `UnsupportedClassVersionError` because the dogfood step loads the just-built formatter in the
   daemon JVM. Point `JAVA_HOME` at a 25 toolchain or add `gradle/gradle-daemon-jvm.properties`.
2. **TDD is mandatory.** For every item: reproduce with a failing test (a fixture under
   `test-fixtures/`, a unit test, a `CliTest` or `LintCompileSafetyTest` case, an IntelliJ fixture
   test), watch it fail for the stated reason, write the minimum fix, watch it pass, run the module's
   `check`, commit. No production code without a failing test. Never skip, `@Disabled`, or loosen a
   test to get green. If a test is hard to write, the design is the problem.
3. **Commits.** One item per commit where possible, single-line Conventional Commits
   (`fix: render type parameters on class, interface and record declarations`), no body, no footer,
   no `Co-Authored-By`. The branch is squashed later, so do not agonise over granularity, but do not
   batch unrelated items either.
4. **Code style.** `final var`, records and sealed interfaces, streams over mutable accumulators,
   `Optional` at boundaries, `@NullMarked` packages, AssertJ only, `@ParameterizedTest` for repeated
   assertions. Read `CLAUDE.md § Code style`.
5. **Output is a contract.** Any change to what javafmt emits lands with a fixture that pins it and
   the matching line in `docs/rules.md` (a one-line doc edit in the same commit is fine; a docs
   sweep is not your job now). If the docs and the code disagree and no plan item or decision says
   which wins, stop and ask.
6. **The verifier is not a safety net you may lean on.** `RoundTripVerifier` turns most renderer
   bugs into a refused file instead of corruption. That is why Tier B items look harmless. Fix the
   renderer, and where the plan says so, strengthen the verifier (A6). Never turn `verifyOutput` off
   to make a test pass.
7. **Verify before you claim.** Never report an item done without running the command that proves
   it and pasting the decisive output line into the **Fixed:** note. If you cannot run it (needs an
   IDE session, a second machine), say so and leave the box unticked with a **Blocked:** note.
8. **Do not change** permissions, `CLAUDE.md` rules, `.claude/`, `gradle.properties` toggles,
   errorprone, NullAway, `-Werror`, or fixtures (except the four D-4 names the plan tells you to
   rewrite, after the owner has confirmed D8).
9. **First commit.** `prompt.md` has a staged deletion; the new file replaces it. Commit
   `PLAN.md`, `prompt.md` and `review/` first as
   `docs: add trustworthiness plan and execution prompt from the 2026-09-19 review`.

## Order of work

`PLAN.md § Order of work` is binding: **B1** (the hang), **B2** (batch isolation), **C1** (corpus
gate) before anything else, because until then you cannot measure. Then Tier A and the rest of
Tier B, then Tier D, then Tiers E and F, then C2. Tier A, Tier B and Tier F are independent once C1
exists; if you can dispatch subagents, run one per tier with a fresh context and this file plus the
relevant `PLAN.md` tier and report as their brief. Inside a tier, top to bottom.

## Per-item loop

```
1. Read the PLAN.md entry and the cited section in review/<report>.md.
2. Reproduce at HEAD. For core and CLI:
     gradle :javafmt-cli:shadowJar
     printf '<input>' | java -jar javafmt-cli/build/libs/javafmt-cli-*-all.jar
   For IntelliJ, the fake-binary fixtures in javafmt-intellij/src/test. For Spotless, a scratch
   Gradle project under the scratchpad directory using publishToMavenLocal.
   If it does not reproduce, write **Not reproducible:** with what you saw and move on.
3. Write the failing test. Fixture pairs: test-fixtures/<name>/input.java + expected.java.
   Canonical-shape-preserved: test-fixtures/idempotent/<name>.java. Name by construct
   ("generic-record-header"), never by bug number.
4. Run only that test; confirm it fails for the stated reason.
     gradle :javafmt-core:test --tests 'io.javafmt.JavafmtFixtureTest'
5. Implement the minimum fix. Extend the existing renderer or rule; do not add a parallel path.
6. Run, in this order, and all must pass:
     gradle :javafmt-core:check          # fixtures, idempotency, differential compile, oracle suite
     gradle spotlessCheck                # javafmt formatting its own source with the new build
     gradle :javafmt-core:corpusTest     # once C1 exists; the summary table must not regress
   plus the client module's check when you touched it:
     gradle :javafmt-cli:check   gradle :javafmt-spotless:check   gradle :javafmt-intellij:check
7. Commit. Tick the box in PLAN.md and append:  **Fixed:** <hash> — <one line of evidence>.
```

## Verification commands worth knowing

```
# Corpus (JDK sources ship with the toolchain; offline)
JH=$(gradle -q :javafmt-core:javaToolchainHome 2>/dev/null || echo "$JAVA_HOME")
unzip -q -o "$JH/lib/src.zip" 'java.base/java/util/*' 'java.base/java/lang/*' -d /tmp/corpus
java -jar javafmt-cli/build/libs/javafmt-cli-*-all.jar --check /tmp/corpus
#   must terminate; exit 0 or 1; no "internal error"; note every "would reformat" count

# Idempotency on a formatted tree
java -jar javafmt-cli/build/libs/javafmt-cli-*-all.jar /tmp/corpus && \
java -jar javafmt-cli/build/libs/javafmt-cli-*-all.jar --check /tmp/corpus   # must be silent, exit 0

# Native binary for the IntelliJ plugin (needs GraalVM 25 on PATH or the toolchain the build pins)
gradle :javafmt-cli:nativeCompile --no-configuration-cache
javafmt-cli/build/native/nativeCompile/javafmt --version

# Hang check for B1 (must finish in well under a second)
time java -jar javafmt-cli/build/libs/javafmt-cli-*-all.jar --check /tmp/corpus/java.base/java/util/stream/Stream.java
```

## Decisions: when to stop and ask

`PLAN.md § Decisions that affect output` lists D2 to D11. **All are decided** (marked **Decided**
in the row), including the loop sub-question under D-3: loops always expand, only `if`/`else` is
preserved; and D11: nested types move to the bottom, the utility-class constructor goes last of
all, nothing else is reordered. Do not ask about policy. Two decisions (D4 member blank lines, D10 module directives) say
"do what google-java-format does": before implementing D-2 and A7, read
`JavaInputAstVisitor.addBodyDeclarations` and `JavaInputAstVisitor.visitModule` in the
google-java-format source (clone `https://github.com/google/google-java-format` into the
scratchpad directory) and confirm the plan's description; if it differs, follow google-java-format
and correct the plan text in the same commit. Do not decide silently; do not re-ask what the plan
already decides.

Also stop and ask when:
- a fix would change formatting output in a way no plan item or decision covers;
- a Tier B fix breaks more than a handful of existing fixtures (they may encode the bug, as the
  four D-4 fixtures do; the owner confirms before you rewrite expected output);
- a reproduction contradicts the report in a way that changes the fix;
- C2 turns up a new class of defect (file it as a new plan item with the reproduction, then ask
  whether to proceed or stop).

## What "done" means

- Every Tier A to F box is ticked with a **Fixed:** note, or carries a **Blocked:**/**Deferred:**
  note the owner has seen.
- `gradle build` for `javafmt-core`, `javafmt-cli`, `javafmt-spotless` and `javafmt-intellij` is
  green on a JDK 25 daemon, including `corpusTest` and `spotlessCheck`.
- C2 has been run on at least one real project and logged at the bottom of `PLAN.md`: zero internal
  errors, project compiles and its tests pass after formatting, second `--check` silent, comment
  diff reviewed, Spotless and IntelliJ agree with the CLI.

Then hand back with: the dogfood log entry, the list of items still blocked, and any output
decisions you had to leave open.
