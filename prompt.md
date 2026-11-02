# Handoff: status of the v1 adoption-feedback reconciliation (2026-11-02)

This file is a handoff note, not part of the project. Do not commit it; delete it when you are done.

Work alone in the main thread: no Agent, Workflow or SendMessage, no `run_in_background`, no other worktrees. The owner has very few tokens left: send long output to files and read counts or the first few lines. `gradle`, never `./gradlew`; one heavy build at a time. Commits are unsigned (`git -c commit.gpgsign=false commit`), single-line Conventional Commits, no trailer. While work is in progress, commit unsigned `wip:` commits, then fold them into their area commits at the end. Read `CLAUDE.md` and `docs/rules.md` first (TDD, no comments in new code, streams, `final var`, AssertJ, complete words, fix bugs instead of documenting them, no repository directory name or absolute paths in tracked files). The owner wants V1 to ship with no known bugs.

## Where things are

The work is on branch `wip/v1-adoption-feedback`, based on `22309a06` (PR #20, rebased onto `main` on 2026-11-01; the work was first done on the earlier PR #20 commit `2c1587f4` and moved over with no conflicts, but has not been built or tested on the new base yet). The integration branch `fix/v1-adoption-feedback` is still at `eee2df6a`. Push only when the owner asks.

Commits on the branch since `22309a06`:

| Commit | Area |
| --- | --- |
| `551cef2d` | fix: enums, `@Nested`, member order |
| `49e08f4f` | feat: lambda hug |
| `4b9bdf28` | fix: statements |
| `b2cafc0f` | fix: annotations |
| `2dfd7f02` | feat: ratchetFrom |
| `02bf577a` | fix: comments (`5925c1ae` + `360c57fe` + the nested-generic fixture) |
| `4165cd6b` | feat: analysis (`aa66203a` + `eee2df6a` + `45680bc4` + the witness fix + one test message) |
| `4f74fb8c` | style: reformat (`570ce68e` + the later `spotlessApply` runs) |
| `9d2872cf` | **wip:** break a declaration after `=`. Unfinished; see "Open work" item 1 |

`4f74fb8c` is the last verified state. Bring it to the integration branch with `git merge --ff-only wip/v1-adoption-feedback`, but only after the WIP commit is finished and folded, or dropped (`git reset --hard 4f74fb8c`). The owner has not yet decided which.

## Done and verified

- **Hashes below** that are not in the commit table (`5925c1ae`, `360c57fe`, `aa66203a`, `eee2df6a`, `45680bc4`, `570ce68e`, agent branch commits) refer to the integration branch and agent branches before the rebase.
- **Step 1, nothing lost.** I compared every file, fixture and test method from the eight 2026-11-01 agent branches with the integration branch. Everything landed except `45680bc4`, which I cherry-picked and verified. The remaining differences come from later commits on purpose:
  - `CommentWeaver` was replaced by `LooseComments` in `360c57fe`;
  - the `--analyze` CLI tests were replaced in `aa66203a`;
  - the `ModifierRenderer`/`ModifierOrder` hoist tests were removed by the annotation commit `b2cafc0f`;
  - the fixtures `implements-line-comments` and `member-order-comment-after-annotations` changed under the comment redesign, with the comment now kept beside the brace or annotation where it was written.

  No agent worktree has uncommitted work. Commits `3d680fee`, `34f1a828` and `5966344d` (cache digest) on two agent branches come from `origin/main`, not this effort. I did not look into the older May and September branches.
- **Bug found and fixed** (in `4165cd6b`): with `45680bc4`, `RedundantTypeArguments` dropped type-annotated witnesses, turning `Stream.<@Nullable T>iterate(...)` into `Stream.iterate(...)`. A witness whose text contains `@` is now kept. There is a test in `RedundantTypeArgumentsAnalyzeTest` and a sentence in `docs/rules.md`.
- **Step 4** (in `02bf577a`): a comment between the two `>` of a nested generic close already stays in place everywhere: fields, `new`, casts, witnesses, bounds and `extends`, with and without the classpath. A source in `comment-placement/` can't hold `> >`, because javafmt joins it into `>>` and the source would no longer format to itself. So I added the idempotent fixture `test-fixtures/idempotent/comments-between-nested-generic-closes.java` instead. It passed on its first run, so no code change was needed.
- **Step 5:** commits folded; after each fold the tree was identical to the one before. The forbidden-word scan over the diff and the commit subjects is clean. I changed one `OracleCorpus` test message from "corpus file" to "file".
- **Step 3 results, at `4f74fb8c`:**
  - `gradle :javafmt-core:test` passes.
  - The `corpusTest` gate: refused 0 (baseline 9), not reparsing 0, not idempotent 0, files over 150 characters 79 (baseline 78). It fails only on the stale baseline.
  - `gradle build --continue` fails only at `corpusTest` and `:javafmt-vscode:npmTestIntegration` (see "Open work" item 3).
  - Two-pass CLI over 50,621 JDK and library files, parse-only and with `--classpath= --threads=4`: pass two reports 0 `would reformat`. The only errors are the two Lombok `var` files, which is why the exit status is 1. No exceptions, no `OutOfMemoryError`, no classpath fallbacks. The analysis pass takes about 8 minutes, and its `--check` pass about 3.
  - None of the JDK's over-width lines comes from where a comment was placed; every one is a long comment's own text.
- **The 79 vs 78 over-width files:** the old jar at `22309a06` and the new one produce the same 79 files on `java.base`. The extra file is one the baseline-era code refused (refused 9 → 0) and now formats. It is not a regression.

## Open work, in order

1. **Declarations whose `Type name = call(` head alone overflows 150.** This is a real bug: the rules only allow string literals past the width. The cases, from formatting the JDK `java.base` parse-only:
   - `sun/util/locale/provider/LocaleProviderAdapter.java:76`;
   - `jdk/internal/util/Preconditions.java:50,58`;
   - `java/lang/invoke/ClassSpecializer.java:891`.

   `jdk/internal/math/FDBigInteger.java:214` is inside a comment and is fine.

   WIP commit `9d2872cf`:
   - adds `Doc.Choice.Fit.FIRST_BREAK`;
   - in `PrintEngine`, takes the preferred layout when its text up to the first possible break fits; otherwise takes the fallback when its line after the hard break fits up to its first possible break (`Reach.NEXT_LINE_FIRST_BREAK`); otherwise takes the preferred layout;
   - in `FieldRenderer.withInitializer`, uses `Choice(" = " + value, " =" + continuationIndent(HARD_LINE + value), FIRST_BREAK)` after the head;
   - makes `BlockRenderer.stripTrailingSemicolonDoc` look inside `Choice` and `Indent`, for `for` and `try` headers;
   - adds the fixture `test-fixtures/declaration-breaks-after-equals`.

   `JavafmtFixtureTest` passes. Still to do:
   - **Performance regression:** `DeepExpressionTest` took 1,027 seconds, and `JavafmtFormatAllTest` 642, in a full `:javafmt-core:test` run with an earlier state of this commit. Not yet profiled or re-timed. Suspect the FIRST_BREAK measurement, which pushes groups in BREAK mode, or the second `fits` call on the fallback, on very deep initializers (`DeepExpressionTest` builds `%s value = <thousands of links>;` fields).
   - Run all of `:javafmt-core:test`, `corpusTest` (expect fewer than 79 over-width files), `spotlessApply` (expect the repository's own code to change, and review it), a `docs/rules.md` line under "Line width", and both two-pass CLI runs.
   - Fold into the right area commit (statements or style), or drop it if the owner says so.
2. **Folded string constants indented with tabs come out oddly indented,** because their text is printed exactly as written. Not started. The earlier note called it out of scope, but the owner now wants no known bugs.
3. **`:javafmt-vscode:npmTestIntegration` fails when the repository sits under a long path:** `listen EINVAL … .vscode-test/user-data/1.14-main.sock`, because macOS limits socket paths to 104 bytes. The test should pass a short `--user-data-dir`, for example under the system temporary directory. Check it from a short checkout path too.
4. **`corpus-baseline.properties`** needs refused 9 → 0 and overWidth.mac 78 → 79, or the new number after item 1. Linux is probably one higher; CI confirms it. Put it in its own commit and ask the owner first.
5. Delete this `prompt.md` when done.

## Notes for the owner, from earlier sessions

- Not verified in a running IDE: the IntelliJ module classpath, and VS Code with a live `redhat.java` extension.
- The Spotless API changed to `create(projectDirectory)`.
- Gradle `javafmtCheck` now builds upstream projects first, because it needs their classes.
- The rules allow lines over 150 characters in generated code.
- The branch `backup/v1-adoption-feedback-before-squash` and the agent branches `worktree-agent-*` from 2026-11-01 can be removed once the owner agrees; everything in them has landed. Do not delete any branch yourself.
- The owner rejected a rule that rewrites `+` concatenation to `.formatted(...)`. Do not add it.

## Inputs and how to recreate them

- **JDK source:** unpack `lib/src.zip` from a Java 25 toolchain (Temurin 25.0.5 was used). With the library sources below, the runs covered 50,621 files.
- **Library sources:** a set of `-sources.jar` files unpacked side by side. Last time this included Lombok 1.18.48, Saxon-HE 12.9 and Ant 1.10.17, among others. Lombok's `lombok/var.java` and `lombok/experimental/var.java` are the two expected errors.
- **Old formatter for comparisons:** build the CLI from `22309a06` in a separate scratch worktree.
- **CLI jar:** `gradle :javafmt-cli:shadowJar -q`, then `java -jar javafmt-cli/build/libs/javafmt-cli-0.1.0-SNAPSHOT-all.jar`.
- **Two-pass run:** copy the inputs fresh each time, format in place, then run again with `--check`. Count `would reformat` on stdout and stderr together, check the exit status, and look at every stderr line that is not a `warning:`. Parse-only uses `--no-analyze`; analysis uses `--classpath= --threads=4`. The analysis format pass takes about 8 minutes and its `--check` pass about 3, so run them as separate commands.
- **Over-width check:** after formatting, list the files with a line over 150 characters and compare old and new jars on `java.base`. To find real code overflows, drop lines that hold a string literal or a comment.
- **Folding without interactive editing:** commit `--fixup=<target>`, then `GIT_SEQUENCE_EDITOR=: git -c commit.gpgsign=false rebase -i --autosquash 22309a06`. Plain commits can be folded with a prepared todo list: `GIT_SEQUENCE_EDITOR="cp <todo file>" git rebase -i 22309a06`. Moving a fix ahead of the style commit causes conflicts. Resolve each one to the original commit's version of the file, then check `git diff <before> HEAD` is empty.
