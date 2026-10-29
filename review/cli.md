# javafmt-cli review

## Summary

The CLI is small, readable, and the happy paths all work: I ran the committed native binary
(`javafmt-cli/build/native/nativeCompile/javafmt`, built 2026-09-07) and confirmed `--version`,
`--help`, stdin→stdout, in-place rewrite, `--check`, directory recursion, `--exclude`, `--cache`,
`--diagnostics=json`, deterministic output ordering, and no mtime churn on unchanged files. The
argument parser is hand-rolled but adequate, and the `--help` text matches `docs/intro.md` flag for
flag. Test coverage of `Cli.run` is genuinely good (37 cases).

It is **not** ready to ship as-is. Three problems would bite on day one:

1. **Nothing publishes the binaries.** `docs/intro.md:44` tells users to grab a platform binary from
   GitHub Releases; `jreleaser.yml` has no `distributions` block and `native.yml` uploads only
   workflow artifacts. The documented install path does not exist.
2. **One bad file kills the whole run.** A `StackOverflowError` in the render pool escapes
   `BatchFormatter.resultFor`, so `javafmt src/` dumps a stack trace, exits 2, and formats *zero*
   files. Reproduced.
3. **Lint warnings never appear in file mode.** `javafmt --check Fall.java` prints nothing;
   `javafmt --check < Fall.java` prints both warnings. The entire documented warnings-only catalog
   is invisible in the mode everyone actually uses.

Counts: 3 P0, 10 P1, 9 P2.

## Findings

### [P0] Native binaries and the fat JAR are never attached to a release
- **Where**: `docs/intro.md:44`, `jreleaser.yml:18-47` (no `distributions:` key anywhere),
  `.github/workflows/native.yml:90-97` and `:135-142`, `.github/workflows/release.yml:39-44`,
  `PUBLISHING.md:105`
- **What**: `docs/intro.md` says "Grab a platform binary from [GitHub Releases]". `native.yml`
  builds `javafmt-<target>` binaries but only calls `actions/upload-artifact`, which produces
  workflow artifacts (login required, 90-day expiry), not release assets. `jreleaser.yml` declares
  `release.github` and `deploy.maven` but no `distributions`, so `jreleaserFullRelease` in
  `release.yml` creates the release with source archives only. `native.yml:99-105` acknowledges
  this as "P0.4", still open. The fat JAR (`shadowJar`) is likewise never uploaded, and
  `javafmt-cli` does not apply `javafmt.publish-conventions`, so it is not on Central either.
- **Why it matters for release**: The single documented way to install the CLI leads to a release
  page with no binaries on it.
- **Suggested fix**: Add a `distributions:` block to `jreleaser.yml` for `javafmt` (type
  `NATIVE_BINARY`, one artifact per target) plus the shadow JAR, and make the release job
  `needs: native` with `actions/download-artifact` feeding `build/jreleaser/assemble`.
- **Effort**: M
- **Confidence**: confirmed

### [P0] A single pathological file aborts the entire batch and nothing gets formatted
- **Where**: `javafmt-core/src/main/java/io/javafmt/BatchFormatter.java:140-152` (`resultFor`
  catches `RuntimeException | AssertionError` only), `:154-164` (`await` rethrows as
  `IllegalStateException`), `javafmt-cli/src/main/java/io/javafmt/cli/Cli.java:164-166`,
  `Main.java:12-16`
- **What**: Reproduced with a 2000-deep parenthesised expression. `javafmt Ok1.java Deep.java`
  prints `javafmt: internal error: formatting failed`, dumps a ~40-line stack trace, exits 2, and
  leaves `Ok1.java` untouched — the writes in `Cli.runFiles` never run because `formatAll` throws
  first. The same input on the stdin path exits 0 (the single-source path catches it), so this is
  specific to the pooled render threads and their smaller default stacks. Per-file `Diagnostic`
  isolation is the whole design (`Diagnostic`'s javadoc: "without a failure in one file aborting
  the rest") and it does not hold for `Error`.
- **Why it matters for release**: `javafmt src/` on any real codebase with one deeply nested or
  machine-generated file formats nothing and shows users a Java stack trace.
- **Suggested fix**: Catch `Throwable` (or at minimum add `StackOverflowError`) in
  `BatchFormatter.resultFor` and route it through `Javafmt.internalErrorResult`, so the file yields
  an `InternalError` diagnostic and the rest of the batch still writes. Add a CLI test with a
  deeply nested fixture asserting the sibling file is still formatted.
- **Effort**: S
- **Confidence**: confirmed

### [P0] Lint warnings are silently discarded for file and directory arguments
- **Where**: `Cli.java:219-249` (`finalizeFormatOutcome` looks only at
  `result.diagnostics().stream().filter(Diagnostic::isError)`), `Cli.java:251-254`
  (`reportDiagnostics` is called only from `runStdin` at `:99`), `docs/rules.md:168`,
  `docs/intro.md:95`
- **What**: Reproduced. For a file with a fall-through `case` and an `equals` without `hashCode`:
  `javafmt --check < Fall.java` prints
  `<stdin>:5:13: warning: case falls through …` and `<stdin>:1:1: warning: class overrides equals…`;
  `javafmt --check Fall.java` and `javafmt --check --diagnostics=json Fall.java` print **nothing**
  and exit 0. `docs/rules.md:168` states an unfixable rule "emits a `Diagnostic.Warning`", and
  `docs/intro.md:95` lists `warning` as one of the JSON `kind` values. Neither is true outside
  stdin mode. This also drops the `FinalParameters` "reassigned parameter" warning and the
  `--analyze` "attribution failed" warning (`Javafmt.java:157-160`) — so a user with a bad
  `--classpath` silently gets a parse-only run and is never told.
- **Why it matters for release**: The warnings-only half of the advertised lint catalog does not
  exist from the CLI's primary entry point, and `--analyze` can silently no-op.
- **Suggested fix**: Call `reportDiagnostics` (or an equivalent per-file emitter using
  `Diagnostic.describe(path)` / `JsonDiagnostics.line`) for every non-error diagnostic in
  `finalizeFormatOutcome`, keeping the exit code unchanged for warnings.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Text-mode file diagnostics lose line:col and severity, and only the first error is shown
- **Where**: `Cli.java:226-232` vs `Cli.java:253`
- **What**: File mode formats the diagnostic as `path + ": " + first.message()`. Stdin mode uses
  `Diagnostic.describe(name)`, which yields `file:line:col: error|warning: message`. Verified:
  `javafmt Z.java` → `Z.java: illegal start of expression`; `javafmt < Z.java` →
  `<stdin>:2:13: error: illegal start of expression`. Only the first error diagnostic is printed;
  the rest are dropped. (`--diagnostics=json` does carry line/col, so only the default text mode is
  affected.)
- **Why it matters for release**: Editors, `grep`-based CI annotations, and humans all expect
  `file:line:col`. A CLI whose default output can't be clicked through looks unfinished next to
  javac.
- **Suggested fix**: Use `path + ":" + d.describeBare()` (or `d.describe(path.toString())`) and
  print every error diagnostic, not just `findFirst()`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `--check` exits 0 when a directory cannot be walked — a silent CI pass
- **Where**: `Cli.java:141-149` (`javaFilesUnder` reports and returns `Stream.empty()`)
- **What**: Reproduced: with a subdirectory at mode 000, `javafmt --check proj` prints
  `javafmt: proj: cannot walk directory: java.nio.file.AccessDeniedException: proj/sub` and
  **exits 0**. The `UncheckedIOException` also aborts the whole `Files.walk`, so readable files
  elsewhere under that root are dropped too. This is the same class of bug the team already fixed
  for stdin check mode (`CliTest.java:284-293` comment).
- **Why it matters for release**: A CI gate reports success having checked nothing.
- **Suggested fix**: Have `javaFilesUnder` signal failure (return an outcome, or track a flag on
  `Args`) so `run` returns 2 when any root failed to walk; consider per-entry error handling via
  `Files.walkFileTree` so one unreadable subtree doesn't discard its siblings.
- **Effort**: S
- **Confidence**: confirmed

### [P1] In-place writes are truncate-then-write, not atomic
- **Where**: `Cli.java:241-246` (`Files.writeString(path, result.output(), UTF_8)`)
- **What**: `Files.writeString` defaults to `CREATE, TRUNCATE_EXISTING, WRITE`. A crash, SIGINT,
  or ENOSPC between truncate and flush leaves the user's source file truncated or empty. The cache
  file, by contrast, *is* written atomically —
  `javafmt-core/.../cache/FileFormatCache.java:28-29`: "Saving writes to a sibling temp file and
  atomically moves it into place so a crashed write never corrupts the existing cache." The same
  care is not applied to the thing that actually matters.
- **Why it matters for release**: A formatter that can eat source files on Ctrl-C is the single
  worst day-one story. The pattern is already in the codebase.
- **Suggested fix**: Write to a sibling temp file and `Files.move(..., ATOMIC_MOVE)` with a
  `REPLACE_EXISTING` fallback, mirroring `FileFormatCache.save()`; preserve POSIX permissions from
  the original.
- **Effort**: S
- **Confidence**: confirmed

### [P1] CRLF input is silently rewritten to LF, and this is documented nowhere
- **Where**: `Cli.java:199` / `:242` (read and write both plain UTF-8, no line-ending capture);
  `javafmt-core/.../parser/JavaParser.java:386` strips a BOM but nothing handles `\r\n`;
  `docs/intro.md` and `docs/rules.md` say nothing about line endings
- **What**: Reproduced: a file written with `\r\n` comes back with `\n` throughout. On a Windows
  checkout with `core.autocrlf=true` (the default), every file in the repo "would reformat", so
  `javafmt --check` fails everywhere and `javafmt` produces a whole-repo diff. Windows is one of
  the three platforms in the CI matrix.
- **Why it matters for release**: Windows users' first run either fails CI on every file or
  produces an unreviewable diff, with no documentation explaining why.
- **Suggested fix**: Detect the dominant line ending on read and re-apply it on write (what
  google-java-format and Prettier do), or at minimum document "javafmt normalises to LF" in
  `docs/rules.md` and add a `--line-ending` escape hatch.
- **Effort**: M
- **Confidence**: confirmed

### [P1] No default excludes — `javafmt .` reformats `build/`, `target/` and generated sources
- **Where**: `javafmt-core/src/main/java/io/javafmt/SourceFiles.java:20-24` (filters on
  `.java` and `isRegularFile` only), `Cli.java:131-139`
- **What**: Reproduced: `javafmt --check proj` reported `proj/build/generated/Gen.java`,
  `proj/target/generated-sources/T.java` and `proj/src/main/java/S.java` alike. There is no
  `.gitignore` awareness and no built-in exclusion of `build`, `target`, `out`, `node_modules`, or
  `generated`. The repo's own `.gitignore` excludes exactly these directories.
- **Why it matters for release**: `javafmt .` is the second thing anyone types, and it rewrites
  generated code (which is then regenerated, producing permanent CI churn).
- **Suggested fix**: Ship a default exclude set (`**/build/**`, `**/target/**`, `**/out/**`,
  `**/node_modules/**`, `**/generated/**`, `**/.git/**`) applied during directory expansion only,
  with a `--no-default-excludes` opt-out, and document it in `docs/intro.md`.
- **Effort**: M
- **Confidence**: confirmed

### [P1] Unhelpful IO error messages: encoding failures and permission failures both read as noise
- **Where**: `Cli.java:201-206` (`"read failed: " + e.getMessage()`), `Cli.java:243-245`
  (`"write failed: " + e.getMessage()`)
- **What**: Reproduced. A Latin-1 byte in a source file gives
  `Latin.java: read failed: Input length = 3` — `MalformedInputException.getMessage()` with no hint
  that the file isn't UTF-8 and no way to override the charset (there is no `--encoding` flag, and
  UTF-8 is hardcoded at `:199` and `:242`). A read-only file gives
  `/path/Ro.java: write failed: /path/Ro.java` — `AccessDeniedException.getMessage()` is just the
  path repeated, with no "permission denied". A dangling symlink gives
  `Broken.java: read failed: Broken.java`.
- **Why it matters for release**: These are the three most common IO failures and none of the three
  messages tells the user what went wrong.
- **Suggested fix**: Switch on the exception type and produce targeted text —
  `MalformedInputException` → "not valid UTF-8", `AccessDeniedException` → "permission denied",
  `NoSuchFileException` → "no such file". Document the UTF-8-only contract in `docs/intro.md`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] The native build never runs on PRs or main, and can't gate the release
- **Where**: `.github/workflows/native.yml:3-6` (`workflow_dispatch` + `push: tags ['v*']`),
  `.github/workflows/build.yml:3-6`, `.github/workflows/release.yml:3-6`
- **What**: `build.yml` runs on PRs but never compiles a native image. `native.yml` runs only on a
  tag, concurrently with `release.yml`, and `release.yml` does not depend on it. A commit that
  invalidates the committed reachability metadata (a new reflective path in javac, a DTO change)
  is discovered only after the tag is pushed, and `release.yml` will have already published to
  Maven Central by then. The CLI's native smoke test (`native.yml:80-88`) is genuinely good — it
  just never runs when it could still prevent anything.
- **Why it matters for release**: The one thing that verifies the flagship artifact runs after the
  release is irreversible.
- **Suggested fix**: Add one native job (e.g. `ubuntu-24.04` only, for cost) to `build.yml` or run
  `native.yml` on PRs touching `javafmt-cli/**`; make the release job `needs:` the native job.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Native platform matrix omits Intel macOS and ARM Linux
- **Where**: `.github/workflows/native.yml:31-37` (`linux-x64`, `darwin-arm64`, `win32-x64`)
- **What**: No `darwin-x64` (Intel Macs) and no `linux-arm64` (Graviton/ARM CI runners, Apple
  Silicon Docker). `docs/intro.md:44` promises "a platform binary" without qualification.
  `buildSrc/.../javafmt.native-conventions.gradle.kts:43-70` has a Docker cross-compile task but
  it is amd64-only and unused by CI.
- **Why it matters for release**: Intel Mac and ARM Linux users hit a 404 and fall back to the fat
  JAR, which is also not published (see the first P0).
- **Suggested fix**: Add `macos-13` → `darwin-x64` and `ubuntu-24.04-arm` → `linux-arm64` to the
  matrix, or state the supported platform list explicitly in `docs/intro.md`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Committed reachability metadata sits at a dead path and is never packaged
- **Where**: `javafmt-cli/javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/reachability-metadata.json`
  (tracked: `git ls-files`; added in commit `319f72c "save"`)
- **What**: The path is doubled — `javafmt-cli/javafmt-cli/...` — so it is outside the `main`
  resources source set and is never packaged. Confirmed: `javafmt-cli/build/resources/main/META-INF/native-image/io.javafmt/javafmt-cli/`
  contains only `.gitkeep`, `reflect-config.json` and `resource-config.json`. The orphaned file is
  the richer, newer-format metadata (it registers `JrtFileSystemProvider`,
  `jdk.internal.misc.Unsafe`, `META-INF/services/java.nio.file.spi.FileSystemProvider`, etc.),
  while the effective metadata (`reflect-config.json`) registers exactly one class.
- **Why it matters for release**: Whoever generated that file believed it was in effect. The
  binary happens to work today, but a future javac path that needs one of those entries will fail
  at run time with nobody able to see why the "committed" metadata didn't help.
- **Suggested fix**: Decide which is authoritative — move the file to
  `javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/` and delete the
  legacy `reflect-config.json`/`resource-config.json` pair, or delete the stray tree. Remove the
  now-empty `javafmt-cli/javafmt-cli/` directory either way.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No `--` terminator and no `-` for stdin
- **Where**: `Cli.java:337-350`
- **What**: Verified against the binary. `javafmt -- E.java` → `javafmt: unknown flag: --`, exit 2
  (the file is not formatted). `echo 'class D{}' | javafmt -` → `-: read failed: -`, exit 2. A
  mistyped single-dash flag falls through to the `files.add` branch (only `--`-prefixed unknowns
  are rejected), so `javafmt -x` reports `-x: read failed: -x` rather than "unknown flag". Paths
  beginning with a single `-` do work (`./-weird.java` formatted fine) only because unknown
  single-dash tokens are treated as paths.
- **Why it matters for release**: `--` is a universal convention; `-` for stdin is what every
  formatter (black, rustfmt, gofmt, prettier) accepts, and scripts pipe through it.
- **Suggested fix**: Treat `--` as "everything after this is a path", map a bare `-` to the stdin
  path, and reject unrecognised single-dash tokens as usage errors instead of silently treating
  them as files.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No end-to-end process test; `Main` is entirely untested
- **Where**: `javafmt-cli/src/test/java/io/javafmt/cli/CliTest.java:422-427` (every test calls
  `Cli.run` in-process); no `ProcessBuilder` anywhere under `javafmt-cli/src/test`;
  `Main.java:8-29` has no test
- **What**: Nothing verifies the real process contract: that `System.exit` propagates the code,
  that a non-zero exit is actually non-zero from a shell, that the `internal error` handler prints
  to stderr and exits 2, that the shadow JAR's `Main-Class` launches, or that the native binary
  behaves like the JVM one. The only native check is the shell smoke test at `native.yml:80-88`,
  which runs only on tags (see above). `javafmt-vscode` has `NativeDaemonRoundTripTest` as
  precedent for a gated native test; the CLI has no equivalent.
- **Why it matters for release**: Exit codes are the CLI's entire CI contract and no test exercises
  them through a real process.
- **Suggested fix**: Add a `ProcessBuilder`-based test against the shadow JAR covering exit 0/1/2,
  and a native test gated on `build/native/nativeCompile/javafmt` existing (skip when absent),
  mirroring `NativeDaemonRoundTripTest`.
- **Effort**: M
- **Confidence**: confirmed

### [P2] Build-plugin versions bypass the version catalog, including GraalVM's
- **Where**: `buildSrc/build.gradle.kts:9-15` (`org.graalvm.buildtools:native-gradle-plugin:0.10.6`
  and four others hardcoded), `gradle/libs.versions.toml` (no `graalvm` entry),
  `javafmt.native-conventions.gradle.kts:62` (`ghcr.io/graalvm/native-image-community:25`),
  `javafmt-cli/build.gradle.kts:21-24` and `native.yml:46-50` (GraalVM 25 pinned twice more)
- **What**: `CLAUDE.md` states "All dependency versions go in `gradle/libs.versions.toml`, nowhere
  else." Five buildSrc plugin versions and three separate GraalVM version pins live outside it.
- **Why it matters for release**: A GraalVM bump has to be made in four places, and a project rule
  is visibly not followed in a repo people will read as a reference.
- **Suggested fix**: Move the buildSrc plugin versions into the catalog (buildSrc can read it via
  `versionCatalogs`), and add a `graalvm` version used by the toolchain spec, the Docker tag, and
  the `setup-graalvm` step.
- **Effort**: M
- **Confidence**: confirmed

### [P2] Docker cross-compile build args have drifted from the real native build
- **Where**: `javafmt.native-conventions.gradle.kts:24-32` vs `:64-68`
- **What**: The Gradle native build passes
  `--initialize-at-run-time=com.sun.tools.javac.file.Locations` with a comment explaining it is
  required ("javac's `Locations` initializer reads JDK paths from the live file system"). The
  `nativeCompileLinuxDocker` task's arg list omits it. Two lists that must agree, maintained by
  hand, in the same file.
- **Why it matters for release**: An unused-by-CI task that produces a subtly different binary is
  a trap for whoever reaches for it.
- **Suggested fix**: Extract the build args into a single `val` used by both, or delete the Docker
  task now that the CI matrix covers Linux.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Check mode on stdin writes the source to stdout on a parse error
- **Where**: `Cli.java:84-90` (javadoc) vs `Cli.java:100-108`
- **What**: The javadoc says "In check mode stdout is the answer channel, not a place to write the
  formatted source … silence plus the exit code is the whole contract." Three lines below, the
  `result.hasErrors()` branch runs before the `parsed.check` branch, so `javafmt --check < Z.java`
  prints the source to stdout and exits 1. Verified.
- **Why it matters for release**: Minor, but the file's own stated contract is contradicted by the
  code under it, and a check-mode caller redirecting stdout gets unexpected content.
- **Suggested fix**: Test `parsed.check` before the `hasErrors` echo, or amend the javadoc to state
  the error case as a deliberate exception.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Every file's content is read into memory before any formatting starts
- **Where**: `Cli.java:160-166`
- **What**: `runFiles` builds a `FileWork` per path with full file contents, then passes the whole
  list to `Javafmt.formatAll`. `BatchFormatter` deliberately chunks at 64 to bound syntax-tree
  memory (`BatchFormatter.java:29-33`), but the CLI defeats half of that by holding every source
  string for the run. With `--analyze` it is worse: `formatAllWithAnalyze` does not chunk at all
  (`Javafmt.java:206-213`, "Peak memory is therefore proportional to the whole run").
- **Why it matters for release**: A monorepo run (tens of thousands of files) can OOM on a default
  heap, and the native image has no `-Xmx` to raise.
- **Suggested fix**: Stream file reads chunk-by-chunk rather than materialising all `FileWork`
  up front, or document a practical file-count ceiling.
- **Effort**: M
- **Confidence**: likely (not measured at scale)

### [P2] Fat-JAR launcher is named `javafmt-cli`, the native binary `javafmt`
- **Where**: `javafmt-cli/build.gradle.kts:9-15` (`application` uses the project name;
  `nativeBinary.imageName = "javafmt"`), producing `build/install/javafmt-cli/bin/javafmt-cli`
- **What**: Two different command names for the same tool depending on how it was installed, and
  the docs only ever say `javafmt`.
- **Suggested fix**: Set `application { applicationName = "javafmt" }`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] The fat JAR's Java 25 requirement is undocumented
- **Where**: `docs/intro.md:42-50`, `README.md:40`
- **What**: The shadow JAR is compiled at class-file version 69 (Java 25); running it on any
  earlier JRE gives a raw `UnsupportedClassVersionError` (observed locally on a Java 21 JRE).
  Neither the README nor the intro mentions a JVM requirement for the fat-JAR route, and the fat
  JAR is described in `ARCHITECTURE.md:38` as "the fallback — still fast, just needs a JVM
  installed".
- **Suggested fix**: State "requires Java 25+" wherever the fat JAR is offered.
- **Effort**: S
- **Confidence**: confirmed

### [P2] No diff output in check mode
- **Where**: `Cli.java:238-240`, `docs/intro.md:66-77`
- **What**: `--check` reports `path: would reformat` and nothing more. There is no `--diff`. Not a
  broken promise (nothing documents one), but every comparable tool has it and CI logs are the
  natural place for it.
- **Suggested fix**: Add `--diff` emitting a unified diff on stdout.
- **Effort**: M
- **Confidence**: confirmed

### [P2] `javafmt` with no arguments on a terminal blocks silently
- **Where**: `Cli.java:69-71`
- **What**: With no file arguments the CLI reads stdin unconditionally, so an interactive
  `javafmt` with no arguments hangs with no prompt or message until Ctrl-D.
- **Suggested fix**: When stdin is a TTY (`System.console() != null`) and there are no file
  arguments, print the usage text and exit 2.
- **Effort**: S
- **Confidence**: confirmed (from code; not testable in a non-TTY harness)

### [P2] `--help` text omits documented behaviour worth stating
- **Where**: `Cli.java:29-51`
- **What**: The usage block matches `docs/intro.md` exactly, but omits: that input must be UTF-8,
  that line endings are normalised to LF, that directory arguments have no default excludes, and
  that `--check` writes nothing to stdout. These are precisely the things users get surprised by.
- **Suggested fix**: Add a short "Notes" section to `USAGE` once the related P1s are settled.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Mutable-accumulator loops where CLAUDE.md asks for streams
- **Where**: `Cli.java:167-174` (`var worstExit` accumulated in an indexed `for`), `Cli.java:282-357`
  (`parse` mutates eleven locals in a `for` with `i++` inside a `switch`)
- **What**: `CLAUDE.md` says "Stream API over mutable accumulators … if you are building a list
  imperatively, express it as a stream pipeline instead." The exit-code fold is a natural
  `mapToInt(...).max().orElse(0)` over an `IntStream.range`. The parser is harder to express as a
  pipeline and the index-advancing `switch` is defensible, but it is the one place in the module
  where the project's own style rule visibly doesn't hold. Everything else in the module follows
  the rules well: `final var` throughout, records for `Args`/`FileWork`/`FileOutcome`, pattern
  matching in `JsonDiagnostics`, `@NullMarked` package-info present, AssertJ-only tests, no
  TODO/FIXME/dead code found.
- **Suggested fix**: Fold the exit code with an `IntStream`; leave `parse` alone or replace it with
  a small argument-token iterator.
- **Effort**: S
- **Confidence**: confirmed

## Coverage notes

**Read fully**: `javafmt-cli/src/main/java/io/javafmt/cli/{Cli,Main,JsonDiagnostics,package-info}.java`;
`javafmt-cli/src/test/java/io/javafmt/cli/{CliTest,JsonDiagnosticsTest}.java`;
`javafmt-cli/build.gradle.kts`; `buildSrc/src/main/kotlin/javafmt.native-conventions.gradle.kts`;
`buildSrc/build.gradle.kts`; `.github/workflows/native.yml` and `release.yml`;
`jreleaser.yml`; `gradle/libs.versions.toml`; all three committed native-image metadata files;
`ARCHITECTURE.md`; `README.md`; `docs/intro.md`; `ROADMAP.md` Phase 4.

**Read as needed** (out of scope, read to verify CLI behaviour): `javafmt-core`'s `SourceFiles`,
`PathFilter`, `Diagnostic`, `FileFormatCache`, `BatchFormatter`, and the relevant parts of
`Javafmt`.

**Empirically verified** by running the committed native binary
(`javafmt-cli/build/native/nativeCompile/javafmt`, dated 2026-09-07, version
`0.1.0-feature-javafmt-improvements-SNAPSHOT`) against temp fixtures in the scratchpad:
`--version`, `--help`, stdin round trip, in-place format, `--check` both outcomes, CRLF input,
read-only file, non-UTF-8 file, mtime stability on clean files, `-`/`--`/`-x`/`./-weird.java`
argument handling, duplicate file arguments, broken symlink, parse error in a batch, deep-nesting
crash, directory walk scope, corrupt cache file, unwritable cache path, unreadable directory,
warning visibility in stdin vs file vs JSON mode. Per the review brief I did not run `gradle`.

**Skimmed**: `.github/workflows/build.yml` (checked whether native compilation is exercised on PRs),
`PUBLISHING.md` (CLI distribution row only), `docs/rules.md` (lint-pass section only).

**Not covered**: the NDJSON daemon — it lives in `javafmt-vscode`
(`javafmt-vscode/src/main/.../NativeDaemonRoundTripTest`, `javafmt-daemon` image name in
`native.yml:106-142`), not in `javafmt-cli`, so it is outside this scope. The `--analyze` path was
exercised only through `CliTest` and an empty classpath, never against a real classpath. Native
binary behaviour on Windows and Linux was not tested (macOS arm64 only). Performance and memory at
monorepo scale were not measured.
