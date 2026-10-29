# Design-pattern review checklist

## Prompt for a fresh context

> You are working through `review/checklist.md` on the branch `feature/design-pattern-improvements`.
> Read `CLAUDE.md` and this whole file before starting. Then work the
> commits below **in order, one at a time**, and do not start the next until the current one is
> committed and its boxes are ticked.
>
> For each commit:
>
> 1. **Verify the claim first.** Each item came from a read-only review that ran no code. Read the
>    cited `file:line`s (they may have drifted) and confirm the problem exists. If it doesn't, or the
>    change would not reduce code size, improve readability, or improve performance, strike the item
>    through, write one line saying why, and move on.
> 2. **Prove every performance item.** "Performance" here means a number, not an argument. Before
>    changing code for a `perf:` item, run the benchmark that covers it (add a JMH case first if none
>    does) with the proof settings from "Before you start". Run it again after. Record both on the
>    item as mean ± JMH's 99.9% error, with the unit. A change counts as a win only if the two error
>    ranges don't overlap. If they overlap, or the change is slower, revert it, strike the item
>    through with the numbers, and move on. Items that also cut code may stay if they are no slower;
>    say so on the item.
> 3. **TDD.** Write the failing test first, then the minimum code, then refactor (`CLAUDE.md`).
>    Pure refactors are covered by the existing fixture and idempotency tests; run them before and
>    after and make sure the output is byte-identical.
> 4. **Build everything.** `gradle build` must pass. This shell does not apply `.sdkmanrc`, so set
>    `JAVA_HOME=~/.sdkman/candidates/java/25.0.5.0+8-tem` and put
>    `~/.sdkman/candidates/gradle/9.8.2/bin` first on `PATH`, and check `gradle --version` says 9.8.2.
>    A task reported `UP-TO-DATE` or `FROM-CACHE` did not run: if you changed something it doesn't
>    declare as an input, rerun it with `--rerun`.
> 5. **Clean-context review loop.** Spawn a fresh subagent with no knowledge of your reasoning. Give
>    it only the item text from this file, `CLAUDE.md`, and the diff (`git diff`), and ask it to find
>    correctness bugs, behaviour changes, style-rule violations and anything the item promised but
>    the diff doesn't deliver. Fix what it finds that holds up, rebuild, and spawn another fresh
>    reviewer. Repeat until a reviewer comes back clean.
> 6. **Commit.** One single-line Conventional Commit per numbered commit below, no body, no
>    `Co-Authored-By` (`CLAUDE.md`). Follow-up fixes to an earlier commit on this branch are folded in
>    with `git commit --fixup` and `git rebase --autosquash`, not added as new commits.
> 7. **Tick the boxes** for the items done in that commit, add the commit hash, and commit the
>    checklist update together with the work.
>
> Do "Before you start" before commit 1. When every commit is done, run the steps at the bottom of
> this file in order: re-run the pattern review, prove the performance and draft the PR description,
> then delete `review/` in one last commit. Nothing on this branch is pushed until that deletion
> commit exists. Stop and ask the user before anything that changes formatting output, public API
> beyond what an item describes, or needs a force-push.

## Before you start: capture the baseline

Do this once, on the branch tip before commit 1, so the "before" numbers come from code no item has
touched. Write down the commit hash you measured.

1. **Proof settings.** The benchmarks are annotated `@Fork(1)`, 3 warmups and 5 one-second
   iterations, which is too little to prove a difference. Add a `-PjmhProof` switch to the `jmh`
   block in `javafmt-core/build.gradle.kts` that sets 3 forks, 5 warmup iterations and 10
   measurement iterations (the plugin's settings override the annotations), and commit it on its own
   as `build: add a JMH proof profile`. Every number in this file uses `-PjmhProof`.
2. **Same conditions every time.** Plugged in, nothing else heavy running, same JDK and Gradle as
   above. Note the machine (`sysctl -n machdep.cpu.brand_string`, core count, memory) at the top of
   the results.
3. **JMH baseline.** Run the whole suite, `gradle :javafmt-core:jmh -PjmhProof`, and copy
   `javafmt-core/build/reports/jmh/results.json` to `review/perf/baseline-jmh.json`.
4. **End-to-end baseline.** JMH can't see CLI file I/O (P12) or the daemon (P14), so also time the
   real tool. Build the CLI with `gradle :javafmt-cli:installDist` (or the shadow jar), then unpack
   the toolchain's `lib/src.zip` `java.base/` into a temp directory as the corpus. Measure each case
   10 times with `/usr/bin/time -l` and record the median wall time and median peak RSS:
   - `--check` on the corpus with no cache (cold),
   - `--check` on the corpus with `--cache` already populated (warm),
   - formatting a fresh copy of the corpus in place (writes),
   - the same three on the repo's own `*/src/main/java`.

   For the daemon, time a fixed batch of 50 concurrent requests through `Daemon.main`. Write a small
   driver that runs from the test or JMH source set, not the shipped code. Save everything as a table
   in `review/perf/baseline-e2e.md`, including the exact commands.
5. **Corpus gate baseline.** `CorpusGateTest` (`gradle :javafmt-core:corpusTest --rerun`) formats
   1,276 `java.base` files and fails if any one file takes over 5,000 ms. On CI that limit is close:
   the slowest file took 3,885 ms (`java/io/BufferedInputStream.java`, 105.7 s total) on PR #9's
   commit `b8a7887`, and 2,333 ms (`java/util/concurrent/ConcurrentHashMap.java`, 95.4 s total) on
   `1168751`. Locally the same gate takes about 15 s with a slowest file of about 0.5 s, so CI is
   roughly 6× slower. If the gate's report shows only the single slowest file, extend it to list the
   10 slowest with their times (a test-only change; put it in the proof-profile commit). Then run it
   3 times locally and record the total, and each of the 10 slowest files, as medians in
   `review/perf/baseline-e2e.md`, next to the two CI figures above.
6. Commit `review/perf/` with the checklist.

## Commits

Items are grouped so related changes land together. The labels (P1–P15 for performance, A–L for
code size and readability) are from the original review. Deleting code comes first because it
makes later performance work smaller.

### 1. `refactor:` delete the unused print strategies and merge the width walkers

- [ ] **A.** Delete `Doc.Fill`, `GreedyPrintStrategy` and the `PrintStrategy` layer. Production
      always passes `PrintStrategy.wadlerLindig()` (`Javafmt.java:69,121,156,198`,
      `BatchFormatter.java:128,181`); nothing constructs a `Doc.Fill` outside tests. Goes:
      `Doc.Fill` (`Doc.java:242-265`, its `permits` entry and switch cases), `PrintEngine.expandFill`
      / `endsMidLine` and the Fill case, `FitPolicy`, `GreedyPrintStrategy.java`, `PrintStrategy.java`,
      the `strategy` parameter threaded through `Javafmt`, and `Printer(int)`'s Greedy default.
      Update the builder unit tests that used `Printer(int)` (any expectation change is real
      divergence from production; investigate it) and the JMH benchmarks (`PrinterFitsBenchmark`,
      `JavafmtFormatBenchmark`). About −200 lines.
- [ ] **Width walkers.** After A, `WadlerLindigPrintStrategy.fitsFlat` (`:124-174`) is the same walk
      as `Doc.flatWidthOrBreak` (`Doc.java:28-62`) with an early exit. Merge them into one walker
      that takes a budget and stops once over it. About −50 lines.

### 2. `fix:` run the single-file path as a batch of one so the --analyze fallback keeps its rules

- [ ] **B.** The stages are written twice: `Javafmt.java:94-109, 120-126, 145-162, 246-252` and
      `BatchFormatter.java:87-118`. Route single-file calls through `formatChunk(List.of(source), …)`.
      A one-file chunk that fails must end in `internalErrorResult`, not recurse.
- [ ] **Bug (test first).** `BatchFormatter.formatOneAtATime` (`:110-116, 126-130`) sends
      already-normalized sources back through `Javafmt.formatWithResult`: it normalizes twice, calls
      `asWritten` twice, and always parses without attribution. So when an `--analyze` chunk fails,
      the retry silently drops the rules that need resolved types, with no warning. Write a failing
      test that shows the dropped rule, then retry each file with the same `parser` function.
- [ ] **One guard per stage.** `render` already catches
      `RuntimeException | AssertionError | StackOverflowError`, so the identical catch in
      `resultFor` (`BatchFormatter.java:179-185`) is dead code. Keep one guard around lint + render
      (`Javafmt.java:247-251, 273, 286-288`).
- [ ] **H.** Replace `Prepared`, `Linted`, `ParseOutcome`, `AttributedOutcome` and `BatchOutcome`
      (`BatchFormatter.java:133-161, 205-240, 259-273`) with one sealed `Staged<T>` with `map`, so a
      failed file skips later stages. Removes the Ok/Failed re-mapping switches and the
      impossible-state `IllegalStateException` at `:159`. Also replace the `config.lint()` branch
      with `(config.lint() ? LINT_ENGINE : NO_RULES_ENGINE).lintAll(contexts)`: that turns N
      single-file reparses into one batched reparse in analyze runs with lint off.

### 3. `refactor:` carry the analysis classpath in JavafmtConfig and cut the format overloads

- [ ] **C.** Add an `analysis` component to `JavafmtConfig`: a sealed `ParseOnly | Attributed(classpath)`.
      It can't be `Optional`, because Spotless serializes the config. Then `formatWithAnalyze` (both
      overloads) and `formatAllWithAnalyze` go away, `CacheKey` (`:26-36`) needs one `of(config)`, and
      the callers stop branching on whether a classpath is present (`FormatRun.java:59-61, 132-134,
      152-155`, `JavafmtFormatterStep.java:67-75, 104-115`).
- [ ] **Pre-parsed overloads.** `formatWithResult(String, ParseOutcome[, config])` and the
      `AttributedOutcome` variant (`Javafmt.java:72-92, 182-201`) have no production callers outside
      `Javafmt`, only 4 tests. They also skip `SourceEncoding.normalize`/`restore`, so a CRLF or BOM
      file takes a different path through them. Make them package-private or delete them and port
      the tests to `formatAll`. The public API goes from 12 methods to about 4.

### 4. `refactor:` share file batching and diagnostics fields across the integrations

- [ ] **F.** The CLI (`FormatRun.java:126-191, 258-263`) and Maven (`AbstractJavafmtMojo.java:117-141,
      147-152`) both read files, check the cache, put `""` in for cache hits, call `formatAll`, and
      line results back up by index, using a `@Nullable readError` and a `cacheHit` flag. Add
      `Javafmt.formatFiles(paths, config, cache, threads)` returning a sealed
      `FileResult = UpToDate | Unreadable(IOException) | Formatted(path, source, result)`. About −55
      lines.
- [ ] **J.** Give `Position` `line()`/`column()` (`Unknown` returns 0), and give `Diagnostic` `kind()`
      and `severity()`. `JsonDiagnostics.java:25-34` and the VS Code `DiagnosticDto.java:9-14` become
      one-liners, and `Diagnostic.describe`/`describeBare` (`:30-41`) stop repeating the
      empty-position check. The JSON output must stay identical; the IntelliJ decoder depends on the
      kind names.
- [ ] **L.** The Maven mojos each repeat three test constructors (`JavafmtMojo.java:17-27`,
      `JavafmtCheckMojo.java:20-30`, mirroring `AbstractJavafmtMojo.java:66-79`) and the same
      error-logging block (`JavafmtMojo.java:44-48`, `JavafmtCheckMojo.java:48-52`). Keep one
      constructor and move the error check into the base class.
- [ ] **P11.** The Spotless step compiles its exclude globs on every file, twice
      (`JavafmtFormatterStep.isExcluded` `:117-121`, from `format` `:100` and `lint` `:167`). Build the
      `PathFilter` once in `buildFunc` and in `LintingStep`, and have `format` reuse `resultFor`
      instead of repeating the classpath check (`:104-115`). Decide on purpose whether `lint` should
      also fall back to parse-only when analysis fails (today it doesn't).

### 5. `refactor:` declare warning rules and run them only on the converging round

- [ ] **D.** `AvoidStarImport`, `CovariantEquals`, `MissingSwitchDefault`, `EqualsHashCode` and
      `StringLiteralEquality` are the same 15-line wrapper. Replace it with a `WarningRule` base,
      e.g. `WarningRule.onNode(MethodTree.class, CovariantEquals::isCovariantEquals, MESSAGE)`. Share
      the duplicated predicates: `isStarImport` (`AvoidStarImport:34`, `ExplodeStarImports:59`,
      `RemoveUnusedImports:58`) and the equals(Object) check (`CovariantEquals:34-43`,
      `EqualsHashCode:42-51`). `StringLiteralEquality` relies on `visitBinaryOperator`, so binary
      chains must still reach it. About −60 to −80 lines.
- [ ] **P2.** Diagnostics from rounds that still edit are thrown away (`LintEngine.java:42-45`), yet
      the 8 warning-only rules walk the tree every round (`LintEngine.applyRound` `:106-116`,
      `LintRules.java:35-42`). Run them only on the round with no edits, plus once in the truncation
      path. `FinalParameters` and `DefaultComesLast` both edit and warn, so they stay every round.
- [ ] **P10.** `AvoidStarImport` (`:20-31`) walks the whole tree to look at imports. Use
      `unit.tree().getImports()` directly.
- [ ] **P6.** `LintScan` (`:16,22`) forces every rule onto `ChainPathScanner`, which allocates a
      `TreePath` per node. 14 rules never call `getCurrentPath()`: AvoidStarImport, CovariantEquals,
      EqualsHashCode, FallThrough, HideUtilityClassConstructor, MissingSwitchDefault, NeedBraces,
      MultipleVariableDeclarations, RedundantSuperCall, OneStatementPerLine, UpperEll,
      StringLiteralEquality, RemoveUnusedImports, DefaultComesLast. Widen `LintScan` to
      `ChainScanner` and switch those rules.

### 6. `perf:` find reassignments once per scope and look up witness calls once

- [ ] **P4.** `Modifiers.reassigned(scope, name)` (`Modifiers.java:33-37`, `ReassignmentDetector`)
      walks the whole block again for every local (`FinalLocalVariable:39`) and parameter
      (`FinalParameters:64`), which is quadratic in block size. Collect all written names in one walk
      and cache the set per scope in an `IdentityHashMap` inside each `apply`.
- [ ] **P13.** `RedundantTypeArguments.java:297` calls `findCallByMethodNameAt` (`:323-335`) for every
      witness call, and each call walks the whole re-attributed tree. Walk once into a map from
      method-name start offset to `TreePath` (keeping the first match, as today). `--analyze` only.

### 7. `fix:` walk method chains with a loop so long builder chains neither overflow nor go quadratic

- [ ] **P5 (test first).** Add `METHOD_CHAIN` (`new StringBuilder().append(1)….toString()`) and
      `FIELD_SELECT` (`a.a.a…`) cases to `DeepExpressionTest.Chain`. `of()` needs to change from "join
      N operands" to "prefix plus N links". Then fix whatever fails:
      - `MethodChainRenderer.authorBrokeChain` (`:64-72`) and `collectLinks` (`:74-92`) recurse once
        per link, and `collectLinks` rebuilds the whole list at every level (`:83`), which is
        O(N²). Build the chain once with `Stream.iterate` down the receivers, as
        `ExpressionRenderers.renderBinary` does, and make `authorBrokeChain` an `anyMatch` over it.
      - If the scanners overflow (`CommentAttacher.Collector`, `ReassignmentDetector`,
        `FinalParameters.MethodCollector`, `ArrayTrailingComma.ArrayCollector` all recurse through
        `visitMethodInvocation` → `visitMemberSelect`), give `ChainScanner` loop-based
        `visitMethodInvocation`/`visitMemberSelect` that keep `TreeScanner`'s visit and `reduce`
        order, with tests like the existing `ChainScannerTest` ones.

### 8. `perf:` stop re-measuring nested chains and share the threshold-group logic

- [ ] **K.** `MethodChainRenderer.java:49-55` and `ExpressionRenderers.java:107-113` repeat the same
      threshold-group calculation (`flatWidthOrBreak`, `hitsThreshold`,
      `preferBreak = authorBroke || flatWidth < 0 || hitsThreshold`, `new Group(contents, preferBreak)`).
      Extract one helper. It can skip the width walk entirely when `authorBroke` is already true.
- [ ] **P3.** Every nesting level calls `Doc.flatWidthOrBreak` on a subtree that contains all deeper
      levels (`MethodChainRenderer.java:49`, `ExpressionRenderers.java:107`,
      `SwitchExpressionRenderer.java:69`). `review/core-engine.md` measured 164 ms for a 4.8k-char
      expression. Add JMH cases first: one for deep nesting, and one each for formatting
      `java/io/BufferedInputStream.java` and `java/util/concurrent/ConcurrentHashMap.java` from the
      toolchain's `src.zip`, the two slowest corpus-gate files on CI. Then either stop the walk once it
      passes the 80-char threshold (only valid when the result is "break" either way; a forced break
      found before 80 must still be reported), or cache widths per node in an `IdentityHashMap`.
      Output must be byte-identical. **Done means** all three JMH cases show a win with
      non-overlapping error ranges, and the corpus gate's slowest-file times drop against the
      baseline. If the 10 slowest files turn out to be slow for another reason, find it with
      `-PjmhProfile=stack` and report it to the user rather than stopping at P3.

### 9. `perf:` cut repeated scans and copies per file

- [ ] **P7.** The output check scans comments up to four times per file: `RoundTripVerifier.verify`
      compares linted source to output, then `Javafmt` (`:315-317`) compares original to output,
      which repeats exactly when lint made no edits. Keep the comment list the parse already built
      on `ParsedUnit` and reuse it. `TokenScanner` (`:35`) also makes a substring per token; compare
      with `regionMatches` instead.
- [ ] **P8.** Large substring copies to test one property: `LambdaRenderer.java:48` copies the whole
      lambda to check for a leading `(` (use `charAt`); `IfRenderer.java:46` copies the whole `if`
      before looking for a newline (check `indexOf('\n', start) < end` first); `EmptyStatement.java:75-80`
      copies and comment-scans every class body each round (scan only the gaps between members).
- [ ] **P9.** `CommentAttacher.java:195-209` walks the tree separately just to find multi-line
      literals; `Collector` (`:298`) already walks the same tree. Give `Collector` a `visitLiteral`,
      and skip the literal pass entirely when there are no comments (`:46`).
- [ ] **P15.** `Javafmt.java:276-277` runs `replaceFirst("\\n+$")` (a regex over the whole file)
      then copies again with `+ "\n"`. Trim trailing newlines inside `stripTrailingWhitespace`'s
      builder instead. Watch the empty-output case.

### 10. `perf:` run whole chunks on the pool and parallelize CLI file I/O

- [ ] **P1.** `BatchFormatter.java:68-70` runs chunks one after another, and `formatChunk`
      (`:104-106`) parses and lints on the calling thread; only render uses the pool. Submit each
      chunk's whole `formatChunk` as one pool task and collect futures in order; `renderInParallel`
      goes away. Size chunks `clamp(ceil(n/threads), 16, 64)` so small runs still use every thread.
      Attributed (`--analyze`) runs stay one chunk, since javac `Types`/`Trees` aren't thread-safe.
      Update the "one chunk at a time" note (`:30-33`); peak memory becomes up to `threads` chunks.
      Measure with `BatchFormatBenchmark`.
- [ ] **P12.** `FormatRun.java:128-130` reads and hashes files one at a time, which is nearly the
      whole run on a warm cache, and `:135-137` writes them one at a time. Parallelize both while
      keeping order. `FileFormatCache` is already thread-safe (`:49, :61`). Honour `--threads` rather
      than silently using the common pool.

### 11. `perf:` let the VS Code daemon format requests concurrently

- [ ] **P14.** `Daemon.java:22-29` formats each request before reading the next line, so one big
      file blocks every request queued behind it. Hand requests to a small fixed pool of platform
      threads (not virtual threads: each would rebuild the per-thread javac file manager) and
      synchronize the `println`. Out-of-order replies are fine; `extension.ts:173` matches by `id`.

### 12. `refactor:` shorten Doc construction and position lookups

- [ ] **E.** Add `ParsedUnit.startOf(tree)`/`endOf(tree)` next to `positionOf`. Replaces the three
      private `start`/`end` pairs (`EnumTrailingComma:121-127`, `MemberOrder:155-161`,
      `EmptyStatement:103-109`) and about 80 inline `(int) unit.sourcePositions().get…(unit.tree(), …)`
      calls.
- [ ] **I.** Add a varargs `Doc.concat(Doc...)` (`new Doc.Concat(List.of(` appears 84 times in
      `builder/`) and a `Doc.BLANK_LINE` constant for the two-hard-line separator rebuilt at
      `ClassLikeRenderer.java:93`, `DocBuilder.java:194` and `ClassBodyRenderer.java:22`.
- [ ] **G.** Share the comment-aware comma list repeated in `MethodRenderer.java:98-110`,
      `MethodChainRenderer.java:132-145` and `EnumRenderer.java:39-47` (one helper in `CommentDocs`),
      make `RecordRenderer.java:31-41` use a `WrappableList` variant, and merge
      `ClassLikeRenderer.buildTypeList` (`:75-77`) with `MethodRenderer.buildThrowsTail` (`:146-149`),
      which differ only in the keyword. About −30 lines.

### Already considered and dropped

Don't redo these:
- Reordering `DocBuilder.dispatch` cases by frequency: unproven gain.
- One shared node index for all lint rules: high risk, and P2 plus P6 get most of the benefit.
- Caching the Spotless format result for `lint`: depends on Spotless calling `format` and `lint` on
  the same content, which nobody has checked.
- Reusing the render pool across calls, virtual threads for render, a Visitor for `Recursor`,
  shared `Doc.Text` constants for speed, and changes to `FormatRun`, `PathFilter`, `SourceFiles`,
  `FormatCache.noop()` or the IntelliJ `ShimDiagnostic`.

## Final step: re-run the pattern review

After commit 12, run the original review again against the new code, to catch anything missed or
anything the changes introduced. Spawn one fresh subagent per lens below, all in parallel, each
read-only (no edits, no builds), each told to read `CLAUDE.md` first and to return up to 5 verified
proposals with the bucket (code size, readability, or performance), exact `file:line`s, a 2–4
sentence change, the expected impact and the risk, discarding anything that fits none of the three
buckets and anything listed in this file. Each report stays under 500 words.

1. Visitor, Double Dispatch, Acyclic Visitor: `DocBuilder.dispatch`, `builder/`, `lint/`,
   `parser/ChainScanner`, `ChainPathScanner`, `CommentAttacher`, `LintScan`.
2. Composite, Interpreter, Combinator: the `doc/` algebra and `printer/`.
3. Rule Engine, Chain of Responsibility, Specification: `lint/`, `PathFilter`, `SourceFiles`.
4. Pipeline, Collection Pipeline, Function Composition: the format pipeline in `Javafmt` and
   `BatchFormatter`.
5. Flyweight, Caching, Object Pool, Data Locality, Lazy Loading: allocation and repeated work in
   `builder/`, `parser/JavaParser`, `printer/`, `Javafmt.render`.
6. Strategy and Template Method: the renderers, `LintScan`, the Maven mojos, the Spotless step.
7. Trampoline and iterative vs recursive design: any recursion left that fails before javac does.
8. Thread-Pool Executor, Fork/Join, Master-Worker, Fan-Out/Fan-In, Producer-Consumer:
   `BatchFormatter`, `FormatRun`, the VS Code daemon.
9. Value Object, Parameter Object, Special Case / Null Object, Facade, Adapter: the public API in
   `Javafmt`, `JavafmtConfig`, `FormatResult`, `Diagnostic`, `Position`, `cache/`, and each
   integration module.

Merge the reports, drop duplicates, verify each surviving proposal against the code yourself, and
append them to this file as a new numbered list of commits in the same format. Then show the user
the new list and ask which to do before starting any of them.

## After the last code commit: prove it and draft the PR description

1. **Head-to-head run.** Numbers taken weeks apart on a laptop drift, so the final comparison runs
   old and new back to back. Make a worktree at the baseline commit
   (`git worktree add ../baseline <hash>`, outside the repo), build it, and run the full JMH suite
   with `-PjmhProof` and every end-to-end case from "Before you start", in each checkout, alternating
   old, new, old, new. Save the results as `review/perf/final-jmh-{old,new}.json` and
   `review/perf/final-e2e.md`. Remove the worktree afterwards.
2. **Corpus gate, locally and on CI.** Include the corpus gate in the head-to-head: total and the
   10 slowest files, median of 3 runs in each checkout. Then, once the branch is pushed, read the gate
   from the PR's CI log (`gh run view <id> --log | grep -A10 "corpus gate"`) and compare it with the CI
   figures from "Before you start". The target is a slowest file on CI under half the 5,000 ms budget,
   so a slow runner can't fail the build on its own. Report the result either way. If the target
   isn't met, say what still dominates, and don't raise the budget to make it pass.
3. **Check it.** Each `perf:` commit claims a win. Its benchmark must show it in the head-to-head,
   with non-overlapping error ranges. Anything that got slower, in any benchmark, is reported, not
   hidden. If a claimed win doesn't hold up, tell the user before going further.
4. **PR description.** Write it to `build/pr-description.md`, which is ignored by git and survives the
   deletion below. Include:
   - a summary of what changed, one line per commit;
   - a before/after table for every JMH benchmark (mean ± error, unit, % change), one for the
     end-to-end cases (median wall time, median peak RSS, % change), and one for the corpus gate
     (total and the slowest files, locally and on CI, against the 5,000 ms budget);
   - the machine, JDK, Gradle, JMH settings, both commit hashes, and the exact commands, so a
     reviewer can rerun it;
   - every slowdown and every item that was dropped for lack of a measurable win.

   Show the user the draft. It is submitted with the PR, so the user approves it before the PR is
   opened or updated.

## Last commit: delete `review/`

When the user has decided on the re-review list and every item they chose is committed, make one
final commit, `docs: remove the review working notes`, before anything on this branch is pushed:

1. `git rm -r review/`, which removes this checklist along with the other review reports.
2. `git grep -n "review/"` and fix every reference that is left. `PLAN.md` and `prompt.md` cite
   these reports in about 15 places. Drop each citation, or move a fact that still matters into the
   entry that cites it. Ask the user whether `PLAN.md` and `prompt.md` should go too, since they are
   the same kind of working notes.
3. `gradle build` still passes, and `git grep -n "review/"` finds nothing.
