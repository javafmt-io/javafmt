# javafmt-intellij review

## Summary (5 to 10 lines: overall readiness verdict and the top 3 issues)

Not ready for a public Marketplace release. The module is well tested at the unit level
(`JavafmtBinary`, `ShimDiagnostic`, settings, configurable — 1152 lines of test for 700 lines of
main code) and the plugin descriptor is mostly well formed, but the integration layer has three
defects that make the plugin either useless or harmful on day one, plus no CI verification and no
publishing path at all.

The plugin is also architecturally different from what `ARCHITECTURE.md`/`docs/rubric.md` describe:
it does **not** call `javafmt-core` in-process. It shells out to a separately-installed native
`javafmt` binary (`javafmt-intellij/build.gradle.kts:25-27`), because javafmt-core is class-file
major 69 and IDEA 2025.2's JBR is 21. That is a defensible decision but it is undocumented and it
invalidates the "the IDE shows what CI checks" claim.

Top 3:
1. Any file that trips a warning-only lint rule is silently left unformatted with a misleading
   "was left unformatted" balloon (`JavafmtExternalFormatProcessor.java:121-124`).
2. The version gate demands javafmt >= 0.2.0, but the repo has no tags and the first release is
   0.1.0 — the plugin will reject the binary it ships alongside (`JavafmtBinary.java:62`).
3. A subprocess is spawned and waited on for up to 10 s on the EDT inside a write action
   (`JavafmtBinary.java:108`), which is an IDE freeze with no cancellation.

## Findings

### [P0] Warning-only lint diagnostics throw away a perfectly good format
- **Where**: `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtExternalFormatProcessor.java:121-124`;
  `javafmt-cli/src/main/java/io/javafmt/cli/Cli.java:99` and `:250-253`;
  `javafmt-cli/src/main/java/io/javafmt/cli/JsonDiagnostics.java:25-30`;
  `javafmt-core/src/main/java/io/javafmt/Diagnostic.java:20-27`
- **What**: `Cli.reportDiagnostics` writes **every** diagnostic to stderr as NDJSON, including
  `Diagnostic.Warning` (rendered as `{"kind":"warning",...}`). Warnings are non-fatal: the CLI still
  exits 0 and still prints the fully formatted source on stdout. The plugin, however, treats a
  non-empty diagnostic list as failure — `if (!formatted.diagnostics().isEmpty()) { report(...);
  return null; }` — so it discards the formatted output, leaves the file untouched and shows a
  balloon saying the file "was left unformatted". The warning-only rules that trigger this are
  common in real code: `HideUtilityClassConstructor`, `MissingSwitchDefault`, `AvoidStarImport`,
  `FallThrough`, `EqualsHashCode`, `EmptyBlock`, `CovariantEquals`, `StringLiteralEquality`
  (see `javafmt-core/src/main/java/io/javafmt/lint/*.java`, all constructing `Diagnostic.Warning`).
  A single utility class with a default constructor is enough to make Reformat Code stop working on
  that file forever.
- **Why it matters for release**: This is the plugin's whole job failing, silently and with an
  inaccurate explanation, on a large fraction of real Java files. It looks like javafmt cannot
  format ordinary code.
- **Suggested fix**: Gate on error-severity only. `ShimDiagnostic` already exposes `kind`; refuse on
  `parse`/`lint`/`internal` and treat `warning` as informational — install `formatted.output()` and
  either drop the warnings or surface them separately (an INFORMATION balloon, or nothing).
- **Effort**: S
- **Confidence**: confirmed

### [P0] The version gate rejects the version the project is about to release
- **Where**: `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtBinary.java:61-62` and `:165-174`;
  `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtExternalFormatProcessor.java:100-109`;
  `javafmt-cli/src/main/java/io/javafmt/cli/Cli.java:66`
- **What**: `MIN_MINOR = 2` and `supportsJsonDiagnostics` returns `major > 0 || minor >= 2`, so any
  0.1.x binary is rejected with "The javafmt binary at ... is too old: javafmt 0.2.0 or newer is
  required." The CLI prints `"javafmt " + JavafmtVersion.VERSION`, which is the Gradle project
  version stamped by `javafmt-core/build.gradle.kts:27-37`. `git tag --list` is empty and the
  current build stamps `0.1.0-...-SNAPSHOT`, and `PUBLISHING.md` describes 0.1.x as the current
  line. If the first public release is tagged `v0.1.0`, every user who installs the plugin plus the
  matching binary gets the "too old" warning and the plugin never formats anything.
- **Why it matters for release**: Day-one dead-on-arrival for the documented happy path, with an
  error message that blames the user's binary.
- **Suggested fix**: Either cut the first public release as 0.2.0, or change the gate to accept the
  release version being shipped (and add a test pinning the gate to `JavafmtVersion.VERSION` so the
  two cannot drift). Also note the gate cannot parse a 4-digit or single-component version — the
  regex `javafmt (\d+)\.(\d+)\.` requires a trailing dot, so `javafmt 1.0` would be rejected.
- **Effort**: S
- **Confidence**: confirmed

### [P0] A subprocess is spawned and waited on for up to 10 s on the EDT under a write action
- **Where**: `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtBinary.java:96`, `:108`, `:52`,
  `:179`, `:195`; `JavafmtExternalFormatProcessor.java:99-111`
- **What**: `ExternalFormatProcessor.format` is invoked synchronously from
  `CodeStyleManagerImpl.reformatText` → `FormattingServiceUtil.formatRanges` →
  `ExternalFormatProcessorAdapter.formatRanges` (verified by disassembling
  `com/intellij/psi/impl/source/codeStyle/CodeStyleManagerImpl.class` and
  `com/intellij/formatting/service/ExternalFormatProcessorAdapter.class` from IDEA 2025.2's
  `app-client.jar`; `reformatText` calls `CheckUtil.checkWritable` and then `formatRanges` inline,
  i.e. on the EDT inside the write action that `ReformatCodeProcessor` opens). Inside that call the
  plugin does `ProcessBuilder(...).start()` and `process.waitFor(10, SECONDS)`. On the first
  reformat it does this **twice** — once for the `--version` probe (`JavafmtBinary.version()`), once
  for the format itself. There is no `ProgressManager.checkCanceled()` anywhere, and the 10 s wait
  is not tied to a `ProgressIndicator`, so the user cannot cancel. "Reformat code on save" over N
  changed files spawns N subprocesses serially on the EDT.
- **Why it matters for release**: A hung or slow binary (a cold NFS mount, an antivirus scan on
  Windows, a binary that never reads stdin) freezes the whole IDE for 10 s with the write lock held.
  Even the happy path pays two process spawns on the UI thread.
- **Suggested fix**: Migrate from `ExternalFormatProcessor` to
  `com.intellij.formatting.service.AsyncDocumentFormattingService` (present in 2025.2's
  `app-client.jar`), which is the platform's supported base class for out-of-process formatters: it
  runs the `FormattingTask` off the EDT with a progress indicator and cancellation, and applies the
  result in a write action for you. At minimum, move the `--version` probe off the EDT and cut
  `TIMEOUT_SECONDS` well below 10.
- **Effort**: M
- **Confidence**: confirmed

### [P1] There is no fallback to IntelliJ's formatter, but the Marketplace text and the settings UI both promise one
- **Where**: `javafmt-intellij/build.gradle.kts:62-72` (the shipped `<description>`, confirmed in the
  patched descriptor inside `build/libs/javafmt-intellij-*.jar!/META-INF/plugin.xml`);
  `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtConfigurable.java:75-77`;
  `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtExternalFormatProcessor.java:82-84`, `:92-98`
- **What**: `FormattingServiceUtil.findService` (disassembled) uses `ContainerUtil.find` — it picks
  the **first** `FormattingService` whose `canFormat` is true and returns it; there is no chaining to
  `CoreFormattingService`. `ExternalFormatProcessorAdapter.formatRanges` calls
  `formatRangeInFile(file, range, false, false)` and discards the return value
  (`lambda$formatRanges$0`, `pop`). So once `activeForFile` returns true, a `null` return from the
  plugin means **nothing happens at all**. Two shipped claims are therefore false:
  (a) "Reformatting a selection ... still goes to IntelliJ's built-in formatter" — in reality
  `isWholeFile` fails and Reformat Code on a selection becomes a silent no-op; and
  (b) "When no binary is found, Reformat Code falls through to IntelliJ's own formatter rather than
  doing nothing" — it does exactly nothing. The same applies to the "too old binary" and parse-error
  paths.
- **Why it matters for release**: Enabling javafmt silently breaks reformat-selection, a daily
  action, and the Marketplace description tells users the opposite of what the plugin does.
- **Suggested fix**: Either actually delegate — return `activeForFile == false` for partial ranges
  and whitespace-only requests is not possible per-call, so instead call
  `CodeStyleManager.getInstance(project).reformatText(...)` via the core service, or (simpler and
  honest) rewrite the description and the settings hint to say a selection reformat and a missing
  binary leave the file unchanged. Add an end-to-end test through `CodeStyleManager.reformatText`
  that asserts the selection behaviour.
- **Effort**: M (behaviour) / S (text only)
- **Confidence**: confirmed

### [P1] Binary lookup uses `System.getenv("PATH")`, which is nearly empty for a Dock-launched IDE on macOS
- **Where**: `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtBinary.java:79-84`
- **What**: `Optional.ofNullable(System.getenv("PATH"))` reads the JVM's own environment. An IDE
  launched from the macOS Dock/Spotlight (or from the Toolbox App) inherits `launchd`'s minimal
  environment, which does not include `/opt/homebrew/bin`, `/usr/local/bin`, `~/.sdkman/candidates`
  or `~/.local/bin`. IntelliJ ships `com.intellij.util.EnvironmentUtil.getEnvironmentMap()` /
  `PathEnvironmentVariableUtil.findInPath(String)` precisely to paper over this by loading the login
  shell's environment.
- **Why it matters for release**: The majority of macOS users who install javafmt via Homebrew or
  SDKMAN will get "No javafmt binary found" even though `javafmt` works fine in their terminal. It
  reads as a broken plugin.
- **Suggested fix**: Use `PathEnvironmentVariableUtil.findInPath("javafmt")` (falling back to
  `EnvironmentUtil.getValue("PATH")`), keeping the configured-path branch as-is. The IntelliJ
  dependency is fine here — this class is already plugin-only code.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `JavafmtConfigurable.refreshStatus()` runs a subprocess on the EDT every time the Settings page is shown
- **Where**: `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtConfigurable.java:59-65`,
  called from `reset()` (`:102-107`) and `apply()` (`:93-99`)
- **What**: `new JavafmtBinary(path).version()` spawns `javafmt --version` and blocks up to 10 s
  (`JavafmtBinary.TIMEOUT_SECONDS`). `Configurable.reset()` is called on the EDT when the Settings
  dialog opens the page, so opening Settings | Tools | javafmt can freeze the dialog. There is also
  no listener on the text field, so the status label is stale until the user presses Apply.
- **Why it matters for release**: Settings dialogs freezing is one of the most visible categories of
  plugin bug, and it is the first screen a new user visits.
- **Suggested fix**: Resolve and probe on a background coroutine/`AppExecutorUtil` pool thread and
  update the label via `invokeLater`; show "checking…" meanwhile. Add a document listener so the
  status tracks what is typed.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Nothing in CI builds, verifies or publishes the plugin
- **Where**: `.github/workflows/build.yml:42-43`, `.github/workflows/release.yml:30-47`;
  `javafmt-intellij/build.gradle.kts:57-84` (no `verifyPlugin`, `signPlugin` or `publishPlugin`
  configuration); `PUBLISHING.md:106`
- **What**: `grep -n "intellij\|verifyPlugin\|publishPlugin\|buildPlugin" .github/workflows/*.yml`
  returns nothing. CI runs `gradle build`, which compiles and tests the module but does not produce
  the distribution — `javafmt-intellij/build/distributions/` does not exist and `build/tmp/` contains
  no `buildPlugin`/`prepareSandbox`/`verifyPlugin` directories (only `patchPluginXml`,
  `instrumentCode`, `buildSearchableOptions`, `jarSearchableOptions`, `composedJar`,
  `instrumentedJar`). The IntelliJ Plugin Verifier has therefore never run against this plugin, and
  `PUBLISHING.md` lists the Marketplace as "separate; future".
- **Why it matters for release**: No compatibility signal at all, no signed artifact, and no
  automated path from a `v*` tag to a Marketplace upload — so the plugin version cannot track the
  Maven/CLI version.
- **Suggested fix**: Add `intellijPlatform { pluginVerification { ides { recommended() } }
  publishing { token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN") } signing {...} }`,
  run `gradle :javafmt-intellij:verifyPlugin` in `build.yml`, and add a
  `gradle :javafmt-intellij:publishPlugin` step to `release.yml` gated on the same `v*` tag.
- **Effort**: M
- **Confidence**: confirmed

### [P1] The docs describe an in-process plugin and never mention the required binary
- **Where**: `docs/rubric.md:16`; `docs/intro.md:38-40`; `README.md:43`; `ARCHITECTURE.md:81`;
  `ROADMAP.md:46-50`; contradicted by `javafmt-intellij/build.gradle.kts:25-27`
- **What**: `docs/rubric.md:16` says "`javafmt-intellij` calls the same core, so what the IDE shows
  is what CI checks." It does not — the build file deliberately omits the `javafmt-core` dependency
  and `BytecodeTargetTest` enforces that omission. The plugin drives whatever `javafmt` binary the
  user happens to have (any version >= 0.2.0), so IDE output and CI output can diverge whenever the
  two versions differ. `docs/intro.md:38-40` tells users to install from the Marketplace and stops
  there — no mention that a separate native binary is required, where to get it, or that the plugin
  is off by default.
- **Why it matters for release**: The rubric page is the competitive-comparison page; an inaccurate
  claim there is the kind of thing a reader checks. And the install page as written leaves a new
  user at a warning balloon.
- **Suggested fix**: Rewrite `docs/intro.md`'s IntelliJ section to cover: install the plugin,
  install the binary (link to Releases/Homebrew), enable under Settings | Tools | javafmt, optionally
  set the path. Correct `docs/rubric.md:16` to say the plugin drives the same binary CI uses, and
  note the version-match requirement.
- **Effort**: S
- **Confidence**: confirmed

### [P2] No plugin icon, no change notes, no vendor email
- **Where**: `javafmt-intellij/src/main/resources/META-INF/` (no `pluginIcon.svg` — the directory
  contains only `plugin.xml`); `javafmt-intellij/src/main/resources/META-INF/plugin.xml:5`;
  `javafmt-intellij/build.gradle.kts:58-84` (no `changeNotes`)
- **What**: `grep -rn "changeNotes\|change-notes\|pluginIcon\|signPlugin" --include="*.kts"
  --include="*.xml"` over the repo returns nothing. The plugin will appear on the Marketplace with
  the default grey placeholder icon, no "What's New" section, and a `<vendor>` element carrying only
  a name and a GitHub URL.
- **Why it matters for release**: Purely cosmetic, but it is the first impression on a Marketplace
  listing page next to polished competitors.
- **Suggested fix**: Add `src/main/resources/META-INF/pluginIcon.svg` (40x40, plus a `_dark`
  variant), set `changeNotes` in `pluginConfiguration` (sourced from a `CHANGELOG.md`), and add
  `email` to `<vendor>`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] No `until-build`, so the plugin claims compatibility with every future IDE
- **Where**: `javafmt-intellij/build.gradle.kts:79-81` (`ideaVersion { sinceBuild = "252" }` only);
  confirmed in the patched descriptor: `<idea-version since-build="252" />`
- **What**: The IntelliJ Platform Gradle Plugin writes no `until-build` when none is configured, so
  the plugin advertises itself as compatible with 252 and everything after. That is a deliberate
  choice some plugins make, but combined with no Plugin Verifier run (above) it means the plugin is
  claiming compatibility with IDE versions it has never been compiled or tested against.
- **Why it matters for release**: A future platform change that breaks `ExternalFormatProcessor`
  ships straight to users as a broken plugin rather than an "incompatible" badge.
- **Suggested fix**: Either set an explicit `untilBuild` and bump it per release, or keep it open but
  wire the Plugin Verifier against `recommended()` IDEs so breakage is caught in CI.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Standalone "Optimize Imports" still runs IntelliJ's optimizer and will fight javafmt's import order
- **Where**: `javafmt-intellij/src/main/resources/META-INF/plugin.xml:16-18` (only
  `externalFormatProcessor` is registered); IDEA 2025.2
  `ExternalFormatProcessorAdapter.getImportOptimizers` returns `Collections.emptySet()`
- **What**: Because the adapter contributes no `ImportOptimizer`,
  `FormattingServiceUtil.findImportsOptimizingService` will skip it and fall through to
  `CoreFormattingService`, i.e. IntelliJ's own Java import optimizer. IntelliJ's default layout
  (static imports last, `java.*`/`javax.*` grouping, on-demand wildcards at 5) differs from javafmt's
  contract (static first, one non-static group, ASCII sort, no star imports). Pressing Ctrl+Alt+O
  therefore produces imports that javafmt will immediately rewrite, and vice versa.
- **Why it matters for release**: A confusing ping-pong for anyone who uses Optimize Imports out of
  habit, and it makes the IDE disagree with CI on a rule javafmt explicitly owns.
- **Suggested fix**: Document the interaction, and/or contribute an `ImportOptimizer` that delegates
  to javafmt so Ctrl+Alt+O produces javafmt's ordering.
- **Effort**: M
- **Confidence**: likely (adapter bytecode confirmed; `findImportsOptimizingService` selection logic
  inferred from its name and the empty-set return, not disassembled)

### [P2] No end-to-end test through the platform's formatting entry point
- **Where**: `javafmt-intellij/src/test/java/io/javafmt/intellij/JavafmtExternalFormatProcessorTest.java:318-330`
- **What**: Every processor test calls `processor.format(...)` directly inside a
  `WriteCommandAction`. Nothing exercises `CodeStyleManager.reformatText(file, ranges)` /
  `myFixture.performEditorAction("ReformatCode")`, so the parts that actually decide whether the
  plugin works in production are untested: extension-point registration, service selection order
  against `CoreFormattingService`, the range that `removeEndingWhiteSpaceFromEachRange` and
  `CoreCodeStyleUtil.RangeFormatInfo` hand to `isWholeFile`, and the format-on-save path. (I
  disassembled `RangeFormatInfo` and confirmed it special-cases the whole-file range via its
  `fromStart`/`toEnd` fields, so `isWholeFile` does hold today — but nothing in the suite would catch
  a regression there.)
- **Why it matters for release**: The one thing that would have caught the "warning aborts the
  format" and "selection is a no-op" defects is the test that does not exist.
- **Suggested fix**: Add one `LightJavaCodeInsightFixtureTestCase` test that enables javafmt, points
  at the fake binary, and calls `CodeStyleManager.getInstance(project).reformatText(file,
  List.of(file.getTextRange()))`, asserting the document changed; add a sibling for a selection range.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Windows is entirely untested
- **Where**: `javafmt-intellij/src/test/java/io/javafmt/intellij/JavafmtExternalFormatProcessorTest.java:28`
  and `JavafmtBinaryTest.java:30` (both `@EnabledOnOs({OS.MAC, OS.LINUX})`);
  `JavafmtBinary.java:82` resolves `javafmt` and `javafmt.exe` only
- **What**: Both subprocess-driving test classes are skipped on Windows, and CI only runs
  `ubuntu-latest` (`.github/workflows/build.yml:10`). `resolve` does not consider `PATHEXT`,
  `javafmt.bat` or `javafmt.cmd`, which is how a JVM launcher script or a Scoop/Chocolatey shim is
  usually installed on Windows.
- **Why it matters for release**: Windows is a large share of IntelliJ users and the plugin's
  process handling (the stdin-writer deadlock workaround in particular) is the most
  platform-sensitive code in the module.
- **Suggested fix**: Add `windows-latest` to the build matrix with a PowerShell/batch fake binary, or
  state the Windows limitation in the plugin description. Add `.bat`/`.cmd` to the candidate names.
- **Effort**: M
- **Confidence**: confirmed

### [P2] Code-style deviations from CLAUDE.md
- **Where**: `javafmt-intellij/build.gradle.kts:34` (`intellijIdeaCommunity("2025.2")` and
  `sinceBuild = "252"` hard-coded, not in `gradle/libs.versions.toml` — CLAUDE.md says "All
  dependency versions go in `gradle/libs.versions.toml`, nowhere else");
  `JavafmtBinary.java:90-93` (`ArrayList` + `add` where CLAUDE.md asks for a `Stream` pipeline);
  `JavafmtExternalFormatProcessor.java:39` (static `VERSION_SUPPORTED` map is never cleared —
  unbounded, though only by one entry per distinct binary identity per IDE session)
- **What**: Minor rule violations, none of them user-visible. The IDE version is duplicated in two
  places in the same file, which is the real risk: bumping one without the other silently produces a
  descriptor that disagrees with what the plugin was compiled against.
- **Why it matters for release**: Maintenance risk, not a user-facing defect.
- **Suggested fix**: Add `intellijIdea = "2025.2"` / `intellijSinceBuild = "252"` to the version
  catalog and read both from there; convert the `command` list to
  `Stream.concat(Stream.of(executable.toString(), "--diagnostics=json"), flags.stream()).toList()`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] No status indicator and a redundant notification title
- **Where**: `JavafmtExternalFormatProcessor.java:177-182`;
  `javafmt-intellij/src/main/resources/META-INF/plugin.xml:24-26`
- **What**: Every notification is titled "javafmt" inside a notification group also displayed as
  "javafmt", so balloons read "javafmt / javafmt: Foo.java was left unformatted". There is no status
  bar widget or editor indication of whether javafmt is active for the current project, so a user who
  forgot to enable it has no signal other than reformat behaving as before.
- **Why it matters for release**: Polish, not correctness.
- **Suggested fix**: Pass an empty title (the group name is already shown) and consider a
  `StatusBarWidgetFactory` showing enabled/disabled plus the resolved binary version.
- **Effort**: S
- **Confidence**: confirmed

## Coverage notes

Read fully: `javafmt-intellij/build.gradle.kts`; all six main sources
(`JavafmtExternalFormatProcessor`, `JavafmtBinary`, `JavafmtSettings`, `JavafmtConfigurable`,
`ShimDiagnostic`, `package-info`); `src/main/resources/META-INF/plugin.xml` plus the patched
descriptor extracted from `build/libs/javafmt-intellij-*.jar`; `JavafmtExternalFormatProcessorTest`,
`JavafmtIntellijTestCase`, `BytecodeTargetTest`, `JavafmtConfigurableTest`;
`.github/workflows/build.yml` and `release.yml`; `settings.gradle.kts` and the root
`build.gradle.kts` version block; `gradle/libs.versions.toml`.

Skimmed: `JavafmtBinaryTest` and `JavafmtSettingsTest` (method names and OS gating only — the
`JavafmtBinary` unit coverage looks genuinely thorough: pipe-deadlock, timeout, interrupt, broken
pipe, non-executable, directory-as-executable); `ShimDiagnosticTest`;
`javafmt-cli/src/main/java/io/javafmt/cli/Cli.java` (stdin path and diagnostic reporting only);
`javafmt-core`'s `Diagnostic`/`FormatResult`; `README.md`, `docs/intro.md`, `docs/rubric.md`,
`ROADMAP.md` Phase 5, `PUBLISHING.md`, `ARCHITECTURE.md`.

Verified against the platform by disassembling IDEA 2025.2's `lib/app-client.jar` (the copy under
`~/.gradle/caches/9.6.1/transforms/736d926c.../ideaIC-2025.2-aarch64`):
`ExternalFormatProcessor`, `ExternalFormatProcessorAdapter`, `FormattingService`,
`FormattingServiceUtil.findService`, `CodeStyleManagerImpl.reformatText`,
`CodeStyleManagerImpl.removeEndingWhiteSpaceFromEachRange`,
`CoreCodeStyleUtil.getRangeFormatInfoList`/`postProcessRanges`/`findElementInTreeWithFormatterEnabled`,
and `CoreCodeStyleUtil$RangeFormatInfo`.

Not covered: I did not run Gradle (per instructions), so I could not confirm which tasks `gradle
build` actually wires in — the "no `buildPlugin`/`verifyPlugin` in `build`" claim rests on the absence
of `javafmt-intellij/build/distributions/` and of those task directories under `build/tmp/`, plus the
grep over the workflows. I did not launch the plugin in a sandbox IDE, so the EDT-freeze and
selection-no-op findings are derived from platform bytecode rather than observed. I did not review
`native.yml`, `perf.yml` or `pages.yml` beyond confirming they never mention the IntelliJ module. I
did not evaluate JBR module-access / `--add-exports` concerns, because `javafmt-core` and
`com.sun.source.tree` are deliberately never loaded in the IDE JVM (enforced by `BytecodeTargetTest`)
— for the same reason there is no slf4j binding conflict, since the module has no slf4j dependency.
