# Parked: release, integration-client and docs backlog

Moved out of `PLAN.md` on 2026-09-19 when the plan was narrowed to formatter trustworthiness.
Everything here is still real and still verified; it just does not gate using javafmt on your own
projects. Item numbers are the ones the original plan used. The Decisions table now lives in
`PLAN.md`; D1, D7 and D11 are release-only and are repeated here.

| # | Question | Recommendation |
|---|---|---|
| D1 | **Parse-error policy across integrations.** Today: CLI exit 1, Spotless build failure, Maven `check` passes, IntelliJ silent no-op, VS Code diagnostics only. | Fail closed everywhere a build gate exists (CLI, Spotless, Maven `check`). Editors show a diagnostic and leave the document unchanged. Document in `docs/rules.md`. |
| D7 | **First public version.** The IntelliJ plugin's gate requires javafmt >= 0.2.0; the repo has no tags. | Tag `v0.1.0` and lower the gate (item 1.6). The gate should be derived from the build, not hand-written. |
| D11 | **IntelliJ fallback claim.** The Marketplace text promises selection-reformat falls back to IntelliJ's formatter; it silently does nothing. | Fix the text for 0.1 (honest), implement real delegation later. |

---

## Phase 1 — Release pipeline (a tag must produce every artifact)

Nothing here is testable end to end until 1.1 lands. Order: 1.1, 1.2, 1.3, then the rest.

- [ ] **1.1 `jreleaser.yml` is never read** (S)
  Root `build.gradle.kts` applies the JReleaser plugin but has no `jreleaser { }` block, and the
  Gradle plugin only reads an external file when `configFile` is set. Verified today:
  `JRELEASER_GITHUB_TOKEN=dummy gradle jreleaserConfig --no-configuration-cache` writes a resolved
  model (`build/jreleaser/trace.log`) with `signing.active: NEVER` and zero occurrences of
  `mavenCentral`. Add `jreleaser { configFile.set(file("jreleaser.yml")) }`. JReleaser 1.15 also
  crashes under the configuration cache (`gradle.properties` enables it), so run every `jreleaser*`
  task in `release.yml` with `--no-configuration-cache`. Verify: the trace shows the `mavenCentral`
  deployer, `signing.active: RELEASE`, and the yml's `longDescription`.

- [ ] **1.2 `javafmt-maven-plugin` has no Maven publication** (S)
  `javafmt-maven-plugin/build.gradle.kts:29-64` only *configures* publications
  (`withType<MavenPublication>().configureEach`); `org.gradlex.maven-plugin-development` 1.0.3 never
  creates one (decompiled and confirmed). `publishAllPublicationsToStagingRepository` stages nothing
  for this module. Apply `javafmt.publish-conventions` and delete the hand-rolled `maven-publish`,
  `signing`, `afterEvaluate` and `withJavadocJar/withSourcesJar` blocks. Confirm the POM has
  `<packaging>maven-plugin</packaging>`. Fix `PUBLISHING.md:117`, which documents the opposite belief.
  Verify: `gradle publishAllPublicationsToStagingRepository -Pversion=0.0.0-test` produces
  `build/staging-deploy/io/javafmt/javafmt-maven-plugin/0.0.0-test/*.jar`, `.pom`, `-sources.jar`,
  `-javadoc.jar`. Add a release-gate step asserting that directory is non-empty.

- [ ] **1.3 `release.yml` must match `build.yml` and depend on the native matrix** (M)
  `.github/workflows/release.yml:19-31` runs bare `gradle build` with no `actions/setup-node` and no
  `xvfb-run`; `build.yml:21-43` needs both because `check` depends on `npmTestIntegration`. Factor
  the build steps into a reusable workflow both call. Make `native.yml` a reusable workflow too, add
  `needs: native` to the release job, `actions/download-artifact` the CLI and daemon binaries for all
  targets into `build/jreleaser/assemble/`, and add a `distributions:` block to `jreleaser.yml` (type
  `NATIVE_BINARY` for `javafmt`, plus the shadow JAR as a `JAVA_BINARY`/`SINGLE_JAR`). Verify with
  the dry run in 1.4: the GitHub release lists one asset per platform plus the fat JAR.

- [ ] **1.4 Dry-run path and signing ownership** (S)
  Add `workflow_dispatch` with a `dry_run` boolean to `release.yml` and pass
  `-Djreleaser.dry.run=${{ inputs.dry_run }}`. Decide signing ownership: Gradle signs staged
  artifacts (`buildSrc/.../javafmt.publish-conventions.gradle.kts:58-65`) *and* `jreleaser.yml`
  declares `signing.active: RELEASE`; keep Gradle signing for Maven artifacts and let JReleaser sign
  only the distributions from 1.3, then check the staging dir has exactly one `.asc` per file. Fix
  `PUBLISHING.md:64-67`, which passes `-P` properties that the plugin reads via `System.getenv`
  (a silent unsigned dry run). Add `prerelease: { pattern: '0\..*' }` to `jreleaser.yml` and
  reconsider `overwrite: true`. Verify: one full `workflow_dispatch` dry run is green before any tag.

- [ ] **1.5 VS Code extension: version, packaging and publishing** (M)
  `javafmt-vscode/package.json:5` hardcodes `"version": "0.1.0"`. Have `packageVsix` and
  `packageVsixTarget` (`javafmt-vscode/build.gradle.kts:151-156`) pass
  `--no-git-tag-version <project.version>` to `vsce package`. Add a release job that downloads the
  `javafmt-daemon-<target>` artifacts into `javafmt-vscode/bin/<target>/`, runs `packageVsixTarget`
  per target with `VSCE_TARGET`, publishes each with `vsce publish --packagePath` and `ovsx publish`
  (`VSCE_PAT`, `OVSX_TOKEN` secrets, documented in `PUBLISHING.md`), and publishes the universal
  jar-fallback VSIX last. Add `javafmt-vscode/README.md`, `CHANGELOG.md`, a copied `LICENSE` (Gradle
  `Copy` task from the root), a 128x128 icon, and `keywords`. Fix `.vscodeignore` so `out/test/**` is
  actually excluded (give tests their own `outDir`). Set `"extensionKind": ["workspace"]`. Verify:
  `unzip -l` on the packaged VSIX shows README, LICENSE, icon, one binary, no test files, and the
  manifest version equals the tag.

- [ ] **1.6 IntelliJ plugin: version gate, verification, signing, publishing** (M, depends on D7)
  `javafmt-intellij/src/main/java/io/javafmt/intellij/JavafmtBinary.java:61-62,165-174` requires
  javafmt >= 0.2.0 and cannot parse `javafmt 1.0`. Derive the minimum from the build (inject
  `project.version`'s major.minor via `buildConfig` or a generated resource) and accept any version
  >= the plugin's own; make the regex accept two- and four-component versions. Add
  `intellijPlatform { pluginVerification { ides { recommended() } } signing { ... } publishing { token = ... } }`
  fed by `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN` env vars; run
  `verifyPlugin` in `build.yml` and `publishPlugin` in `release.yml`; document the four secrets in
  `PUBLISHING.md`. Decide `untilBuild` explicitly (the patched descriptor currently has no
  `until-build`). Add `pluginIcon.svg` (+ `_dark`), `changeNotes`, vendor email. Verify: the
  verifier passes against `recommended()` IDEs in CI.

- [ ] **1.7 CLI distribution details** (S)
  `javafmt-cli` applies no publish convention, so the shadow JAR goes nowhere (covered by 1.3's
  distributions). Set `application { applicationName = "javafmt" }` so the fat-JAR launcher is not
  `javafmt-cli`. Fix the native-image metadata: the richer `reachability-metadata.json` is tracked at
  the dead path `javafmt-cli/javafmt-cli/src/main/resources/...` (outside every source set) while the
  effective directory has a 123-byte `reflect-config.json`. Move it to
  `javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/`, delete the legacy
  pair and the stray tree, fix the documented `metadataCopy` command in `javafmt-cli/build.gradle.kts:39-42`
  and `native.yml:14-20`, and confirm the smoke test still passes. Add `darwin-x64` (`macos-13`) and
  `linux-arm64` (`ubuntu-24.04-arm`) to the matrix or list supported platforms in `docs/intro.md`.
  Unify the three GraalVM version pins (`javafmt-cli/build.gradle.kts:21-24`, `native.yml:46-50`,
  `native-conventions:62`) under one catalog entry, and add one `ubuntu` native job to `build.yml`
  so metadata regressions are caught before a tag.

- [ ] **1.8 Manual account work (not code)** — carried over verbatim from the previous plan
  1. Create a Central Portal account at central.sonatype.com.
  2. Register the `io.javafmt` namespace; the Portal issues a Verification Key.
  3. Add a DNS TXT record on `javafmt.io` with that key; verification takes about five minutes and
     the record can be removed afterwards.
  4. Generate a Portal user token; its username/password become `MC_USERNAME` / `MC_TOKEN`.
  5. Publish the signing key (`rsa4096/3264A646D762952F`, expires 2028-09-12) to
     `keyserver.ubuntu.com` and `keys.openpgp.org`; Central fetches the public key from keyservers.
  6. Set repo secrets: `MC_USERNAME`, `MC_TOKEN`, `JRELEASER_GPG_SECRET_KEY`,
     `JRELEASER_GPG_PUBLIC_KEY`, `JRELEASER_GPG_PASSPHRASE` (full armored text, `mode: MEMORY`),
     plus `VSCE_PAT`, `OVSX_TOKEN` (1.5) and the four JetBrains secrets (1.6).
  7. Register the `javafmt` publisher on the VS Code Marketplace and Open VSX, and the vendor on
     JetBrains Marketplace. Confirm the `javafmt-io/javafmt` GitHub org and repo exist and are the
     `scm` target in every POM and manifest.

- [ ] **1.9 The dogfood check requires a JDK 25 Gradle daemon** (S)
  `gradle build` on a machine whose daemon runs JDK 21 fails every `spotlessJavaCheck` with
  `UnsupportedClassVersionError (class file version 69.0 ... up to 65.0)` because
  `buildSrc/src/main/kotlin/JavafmtFormatter.kt` loads the just-built formatter *in the daemon JVM*.
  Add `gradle/gradle-daemon-jvm.properties` (daemon toolchain 25) and the foojay resolver in
  `settings.gradle.kts` so Gradle provisions JDK 25 itself, or fail fast in `settings.gradle.kts`
  with a message naming the requirement. Document in `CONTRIBUTING.md` (5.10).

- [ ] **1.10 `npmTestIntegration` downloads an unpinned VS Code at test time** (S)
  Failed locally with `spawn .../.vscode-test/vscode-darwin-arm64-1.138.0/.../Electron ENOENT`
  (only 1.136.1 was on disk). Pin the VS Code version in `.vscode-test.mjs` to the one matching
  `engines.vscode`, and either make the task skip with a clear message when the download is
  unavailable or document that it needs network. Fix the task description in
  `buildSrc/.../javafmt.npm-conventions.gradle.kts:32-39`, which claims a skip that does not exist.
  Also set `npmInstallCommand.set("ci")` in both node blocks and pin an exact Node version in CI
  (`build.yml:21-24` uses `lts/*`).

---


## Phase 2 — Integration correctness (day-one bugs in each client)


CLI items 2.1 to 2.10 moved into `PLAN.md` Tier E. IntelliJ items 2.18 to 2.22 and Spotless items 2.14 to 2.17 moved into `PLAN.md` Tier F (they are repeated below unchanged for reference; work them from `PLAN.md`). The IntelliJ version-gate part of 1.6 is `PLAN.md` F2. Maven plugin and VS Code items below remain parked.

### Maven plugin (`javafmt-maven-plugin/src/main/java/io/javafmt/maven/`)

- [ ] **2.11 `check` passes files that do not parse** (S, depends on D1)
  `JavafmtCheckMojo.java:47-53`; the test at `JavafmtCheckMojoTest.java:78-84` pins the current
  behavior. Per D1, fail with a `MojoFailureException` listing the files, and switch check
  failures generally from `MojoExecutionException` to `MojoFailureException`. Save the cache before
  throwing (`:91-95`) so a repo with one dirty file does not re-hash everything each run.
- [ ] **2.12 Missing `skip`, `includes`, `encoding`, extra source roots, help goal** (S)
  `AbstractJavafmtMojo.java:28-56,88-90,136-145`. Add `@Parameter(property = "javafmt.skip")`,
  `includes`, `encoding` defaulting to `${project.build.sourceEncoding}`, walk
  `project.getCompileSourceRoots()` / `getTestCompileSourceRoots()` minus anything under
  `build.directory`; set `mavenPlugin { helpMojoPackage.set("io.javafmt.maven") }` so `mvn javafmt:help`
  works; replace `{@code}`/`{@link}` in `@Parameter` javadoc with HTML so the descriptor is readable.
- [ ] **2.13 Maven integration test** (M)
  No test runs `mvn`. Add a `maven-invoker`-style IT (or `maven-plugin-testing-harness`) that runs
  `format` and `check` against a fixture POM from the locally staged plugin, covering parameter
  injection, `${javafmt.*}` property binding, goal prefix and lifecycle binding.

### Spotless step (`javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java`)

- [ ] **2.14 The documented Gradle snippet does not work and defeats up-to-date checking** (S)
  `docs/intro.md:24-32` shows `custom("javafmt") { apply(input) }` with no buildscript classpath;
  Spotless keys a `custom` step's state on `bumpThisNumberIfACustomStepChanges`, not on the javafmt
  version, so upgrades never invalidate. Document `buildscript { dependencies { classpath("io.javafmt:javafmt-spotless:<v>") } }`
  plus `spotless { java { addStep(JavafmtFormatterStep.create()) } }`. Verify the snippet in a
  scratch project before release.
- [ ] **2.15 Spotless users never see lint warnings** (M)
  `:54-64,73-84` drops `result.warnings()`. Implement `FormatterStep.lint(String, File)` (present in
  `spotless-lib` 4.5.0) mapping warnings to `com.diffplug.spotless.Lint`.
- [ ] **2.16 API scope and analyze path** (S)
  `javafmt-spotless/build.gradle.kts:9`: `implementation(project(":javafmt-core"))` publishes core
  as `runtime` scope although `JavafmtConfig` is in the public signature; use `api`. Add a
  `create(JavafmtConfig, String analysisClasspath)` overload so external users can enable the
  attribution-dependent rules that only `buildSrc/JavafmtFormatter.kt` can reach today.
- [ ] **2.17 Spotless integration test** (M)
  Add a Gradle TestKit `integrationTest` source set that applies Spotless, adds the step, runs
  `spotlessApply`, asserts output, then asserts a second run is up-to-date and that bumping the
  javafmt version invalidates it.

### IntelliJ plugin (`javafmt-intellij/src/main/java/io/javafmt/intellij/`)

- [ ] **2.18 Warning-only lint diagnostics discard a good format** (S)
  `JavafmtExternalFormatProcessor.java:121-124` treats any non-empty diagnostic list as failure and
  shows "was left unformatted". Gate on `kind` in `{parse, lint, internal}` only; install
  `formatted.output()` when only warnings are present. Test with a fake binary emitting a warning.
- [ ] **2.19 Subprocess on the EDT inside a write action** (M)
  `JavafmtBinary.java:52,96,108,179,195`, `JavafmtExternalFormatProcessor.java:99-111`,
  `JavafmtConfigurable.java:59-65,93-107`. Two spawns per first reformat (version probe + format),
  10 s uncancellable wait, holding the write lock; Settings page also probes on the EDT. Migrate to
  `com.intellij.formatting.service.AsyncDocumentFormattingService` (runs off the EDT with
  cancellation, applies the result in a write action); cache the version probe; move the Settings
  probe to a pooled thread with `invokeLater` and a document listener.
- [ ] **2.20 Binary lookup misses Homebrew and SDKMAN on macOS** (S)
  `JavafmtBinary.java:79-84` reads `System.getenv("PATH")`, which is launchd's minimal environment
  for a Dock-launched IDE. Use `PathEnvironmentVariableUtil.findInPath("javafmt")` /
  `EnvironmentUtil`. Also consider `.bat`/`.cmd` candidates on Windows (`:82`).
- [ ] **2.21 Fallback claims are false** (S, depends on D11)
  `build.gradle.kts:62-72` description and `JavafmtConfigurable.java:75-77` promise fallback to
  IntelliJ's formatter for selections and missing binaries; the platform picks the first
  `FormattingService` and never chains, so both are silent no-ops. Rewrite the text for 0.1 and add
  one end-to-end test through `CodeStyleManager.reformatText` for whole-file and for a selection.
- [ ] **2.22 Docs describe an in-process plugin** (S)
  `docs/rubric.md:16`, `docs/intro.md:38-40`, `README.md:43`, `ARCHITECTURE.md:81`. The plugin
  shells out to a separately installed native `javafmt` (deliberate: core is class-file 69, JBR is
  21; enforced by `BytecodeTargetTest`). Say so, and give the install sequence: plugin, binary,
  enable under Settings | Tools | javafmt, version-match requirement.

### VS Code extension (`javafmt-vscode/src/extension.ts` unless noted)

- [ ] **2.23 No `error` listener on the spawned daemon** (S)
  `:164-195` subscribes only to `exit`. ENOENT (no `java`) or EACCES (binary lost its exec bit)
  emits `error` with no guaranteed `exit`, so every format promise hangs forever while the status bar
  shows ready (`:167` sets ready optimistically). Add `proc.on('error', ...)` rejecting all pending
  requests and surfacing the message; set ready only after the first successful response. Unit test
  via the mocked child process emitting `error`.
- [ ] **2.24 No request timeout; the daemon drops failing requests silently** (M)
  `:252-258` (no `setTimeout` anywhere), `src/main/java/io/javafmt/vscode/Daemon.java:21-30` logs and
  emits no response on `RuntimeException`; `Error` kills the loop. Add a per-request timeout that
  deletes the `pending` entry and rejects; on the daemon side always emit a response line (add an
  `error` variant to the protocol with the request id) and catch `Throwable`. Add a protocol
  `version` field and validate response shape in `parseResponse` (`protocol.ts`).
- [ ] **2.25 `stdin` write errors and crash loops** (S)
  `:256` has no `stdin.on('error')` (EPIPE becomes an uncaught exception); `:124-127,183-194`
  respawn on every request with no backoff. Attach the listener, pass a write callback that rejects
  the matching request, stop after three consecutive spawn failures with one sticky error and a
  "Restart daemon" action. Move the pending-drain inside the `daemon === proc` guard (`:185`).
- [ ] **2.26 Daemon stderr is discarded** (S)
  `:180-181`. Create a `javafmt` output channel, append stderr, add `javafmt.showOutput`, point the
  error-state tooltip at it.
- [ ] **2.27 Whole-document replacement and CRLF** (M)
  `:129,141-146`. One full-range `TextEdit` collapses folds and moves the cursor on every reformat;
  on CRLF documents the `output === source` guard can never hit. Emit minimal edits (common
  prefix/suffix trim, then line-level diff). CRLF is solved in core by 2.10; until then convert
  around the request.
- [ ] **2.28 `javafmt.formatOnSave` semantics, `enable`, cancellation, config scope, stale diagnostics** (S)
  `:57-65` is an `onWillSave` participant, not the settings shorthand its description claims, and
  can double-format and blow the 1.5 s budget on a cold jar daemon (pre-warm at activation);
  `:40-55,123` still registers providers when disabled; the `CancellationToken` is not declared
  (`:42-54`); `getConfiguration('javafmt')` has no resource scope (`:62,123,150`); diagnostics are
  never cleared on close (`:140`). Fix each; tests for the correlation logic (`:169-207,252-258`),
  which is entirely untested today.
- [ ] **2.29 Targeted VSIX fallback message** (S)
  `build.gradle.kts:141-149` deletes the jar from targeted VSIXes, so `:240-244`'s fallback shows
  `run "gradle :javafmt-vscode:build"` to end users. Reword for users (install a JDK or file an
  issue) and make sure the universal VSIX is published last as the catch-all (1.5).
- [ ] **2.30 `stopDaemon` leaks the readline interface** (S)
  `:169,197-207`. Keep the `rl` handle, `rl.close()` and `stdin.end()` before `kill()`.

---


## Phase 4 — Public API surface (freeze before Central)

- [ ] **4.1 Module boundary** (M) — 13 public types in `parser/`, 33 in `lint/`, plus
  `JavaModifiers`, are public only for intra-jar access; integrations import only
  `io.javafmt.{Javafmt, JavafmtConfig, FormatResult, Diagnostic, Position, PathFilter, SourceFiles, JavafmtVersion}`
  and `io.javafmt.cache.{FormatCache, FileFormatCache}`. Add `module-info.java` exporting only
  `io.javafmt` and `io.javafmt.cache` (`requires jdk.compiler`; check Lombok, errorprone and NullAway
  still run), and `Automatic-Module-Name: io.javafmt.core` (and equivalents) in every jar manifest
  via `javafmt.java-conventions`. If module-info proves hostile, at least mark `parser`, `lint`,
  `doc`, `printer`, `builder` internal in their `package-info.java`.
- [ ] **4.2 Printer defaults and dead strategy** (S) — `printer/Printer.java:15-17` one-arg
  constructor defaults to `GreedyPrintStrategy`, which production never uses; `Doc.Fill`
  (`doc/Doc.java:206-232`, `printer/PrintEngine.java:89-148`) is constructed by nothing. Default to
  Wadler-Lindig; delete `Fill` and the greedy strategy or move them behind the module boundary.
- [ ] **4.3 Speculative and misplaced API** (S) — delete `JavaParser.HAS_JAVAC_INTERNAL_ACCESS`
  (`parser/JavaParser.java:68`, referenced only by a test asserting `false`); make
  `ParseException.diagnostics` (`:14`) non-transient or drop `Serializable`; drop the `@Nullable`
  early return in `MethodRenderer.render:20-23` that `DocBuilder.dispatch:299-301` rejects anyway;
  if `JavafmtConfig` is still serialized inside the Spotless step's `State`, give it
  `@Serial private static final long serialVersionUID = 1L;`.
- [ ] **4.4 Test-fixtures variant leaks to Central** (S) — `javafmt-core/build.gradle.kts:10`
  applies `java-test-fixtures`; `publish-conventions:16` publishes `components["java"]`. Skip the
  `testFixturesApiElements`/`RuntimeElements` variants.
- [ ] **4.5 Cache key ignores config** (S) — `cache/FileFormatCache.java:43` header is version only;
  a plain run followed by `--analyze` skips every file. Fold a hash of the effective config
  (analyze, classpath, `lint`, `verifyOutput`) into the header; give `save()`'s temp file (`:135`) a
  unique suffix.
- [ ] **4.6 Javadoc and contracts** (S) — state thread-safety on `Javafmt` (and add an N-thread
  identical-output test); Javadoc `Javafmt`, `format`, `FormatResult`, `JavafmtConfig` for the
  javadoc jar; add `formatWithAnalyze` overloads taking `JavafmtConfig` (`Javafmt.java:166-185`
  hard-code defaults); fix `publish-conventions:19` so each module's POM description is not the root
  project's (use a provider).

---

## Phase 5 — Docs, repo and CI readiness

- [ ] **5.1 `docs/rules.md` is the contract; make it true** (S) — add the shipped rules
  `RedundantSuperCall`, `RedundantTypeArguments`, `ExplodeStarImports` (the last two active only
  under `--analyze`); fix the wildcard-import paragraphs (`:44`, `:207`); fix `RedundantModifier`
  ("`final` on methods of a final class", not "on private methods"; records are not handled); fix
  the record wrap example (`:120-124` shows +4, implementation is +8); note the Java 25 module-import
  group renders first; add sections for parse failures (return unchanged + diagnostic + exit 1),
  line endings and encoding (D2), the comment-directive safelist promised at `:171` (not
  implemented: implement or remove), and the lint pass being invisible in Spotless until 2.15. Add a
  test asserting the documented rule list equals `LintRules.DEFAULTS`.
- [ ] **5.2 `CLAUDE.md` consistency** (S) — "Enums: alphabetically sorted" is wrong (declaration
  order, deliberately); method-chain rule omits the 80-char/three-link threshold
  (`MethodChainRenderer.java:27,29`); "Annotations/Javadoc left alone (v1)" is stale; "two LSP
  plugins" (settings enable three); slf4j claim ("implementation dep in all modules"; core does not
  use it). Reduce the formatting-rules section to a pointer at `docs/rules.md` plus real differences.
- [ ] **5.3 `docs/intro.md` install page** (S) — Gradle snippet (2.14); Maven snippet needs
  `<executions>` with the `check` goal and the `mvn javafmt:check` short form; IntelliJ section
  must describe the binary requirement (2.22); fat JAR route must say "requires Java 25+" and the
  build JVM (not just target) must be 25+ for every Maven/Gradle consumer; list supported native
  platforms; add a `Javafmt.format(...)` Java API example; hedge or remove Marketplace instructions
  until 1.5/1.6 have run once.
- [ ] **5.4 README** (S) — badge row, quick-start with Maven and Spotless snippets inline, link to
  `https://javafmt.io`, build prerequisites (JDK 25 daemon, SDKMAN, no wrapper).
- [ ] **5.5 `ROADMAP.md` and `ARCHITECTURE.md`** (S) — mark phases 1 to 5 shipped; close open
  questions 1, 2 and 4 (all decided in code); move `--threads` and excludes out of "future"; add
  `javafmt.spotless-conventions.gradle.kts` and `JavafmtFormatter.kt` to the module tree; correct
  the IntelliJ in-process claim (`:81`). Delete the stale carve-out paragraph in
  `javafmt.spotless-conventions.gradle.kts:9-11` that contradicts `java-conventions:11-15`.
- [ ] **5.6 Website tagline says Java 21+** (S) — `website/docusaurus.config.ts:7` (and
  `website/src/pages/index.md`); everything else says 25+. Add `docs/**` to `pages.yml`'s `paths:`
  so doc edits redeploy `javafmt.io`.
- [ ] **5.7 `PUBLISHING.md`** (S) — add the VS Code and IntelliJ rows and secrets; fix the dry-run
  recipe (1.4) and the Maven-plugin publication note (1.2); describe the dry-run workflow.
- [ ] **5.8 Community files** (M) — `SECURITY.md` (disclosure channel), `CONTRIBUTING.md` (build
  prerequisites, TDD, AssertJ, commit format, `gradle` not `./gradlew`), `CODE_OF_CONDUCT.md`,
  `.github/ISSUE_TEMPLATE/`, `PULL_REQUEST_TEMPLATE.md`, `CODEOWNERS`.
- [ ] **5.9 Workflow hardening** (S) — top-level `permissions: contents: read` in every workflow
  (`build.yml` runs untrusted PR code with default token scope); `concurrency` groups on `build.yml`
  and `native.yml`; align action majors (`build.yml` on v5/v6, the rest on v4) and SHA-pin at least
  `release.yml`; add a nightly `schedule:` to `native.yml`; Dependabot `gradle` (root and
  `buildSrc`) and `npm` (`javafmt-vscode`, `website`) ecosystems.
- [ ] **5.10 Version catalog discipline** (S) — `buildSrc/build.gradle.kts:9-15` hardcodes five plugin
  versions (four duplicate catalog entries; GraalVM native plugin has none);
  `javafmt-intellij/build.gradle.kts:31,34,79-81` hardcodes `2025.2` and `252`. Route all through
  `libs.versions.toml` (the catalog is already wired in `buildSrc/settings.gradle.kts:6-10`).
- [ ] **5.11 Small hygiene** (S) — `LICENSE` says "James Schneidereit", everything else "Jim";
  `perf.yml` has never had a baseline (commit `.github/perf-baseline.json` after the first clean
  scheduled run); Docker cross-compile args in `native-conventions:64-68` drifted from `:24-32`
  (share one `val` or delete the task); `JsonDiagnostics.escape` uses a `StringBuilder` accumulator
  (CLAUDE.md prefers a stream pipeline); mutable-accumulator loops in `Cli.java:167-174`.

---

## Deferred (P2, after release)

`Diagnostic.LintError` is never constructed (wire or remove); `DocBuilder.visitCatch` is dead;
`UtilityClassConstructor` predicate gaps (instance initializer blocks, non-static inner classes);
`EmptyBlock` scope differs from Checkstyle's; IntelliJ `ImportOptimizer` ping-pong with
Ctrl+Alt+O, status-bar widget, Windows test coverage, `.bat`/`.cmd` shims; VS Code `@types/vscode`
pinned exactly to `engines.vscode`, remote/WSL smoke test, `bin/` fail-fast in `packageVsix`;
CLI `--diff`, streaming file reads for monorepo-scale runs; multi-line annotations flattened
(`ModifierRenderer.annotationText`, needs `Doc.Verbatim`); lint-rule test-coverage gaps listed in
`review/core-api-lint.md` and the previous plan; foojay toolchain provisioning for contributors
(folded into 1.9 if not done there); `javafmt.spotless-conventions.gradle.kts:65-73` resolves
`compileClasspath` at configuration time and swallows every failure (silently degrades dogfooding to
parse-only lint); `verifyPluginStructure` warns that the plugin id `io.javafmt.intellij` should not
contain "intellij" (rename before the first Marketplace upload, since ids are permanent); IntelliJ
notification title duplicates the group name.

---

## Go-live sequence

1. Phase 0 complete; corpus gate (0.10) green on `java.base`.
2. Phase 1.1 to 1.4 complete; one `workflow_dispatch` dry run green, staging dir inspected
   (one `.asc` per artifact, Maven plugin present, `<packaging>maven-plugin</packaging>`).
3. Phase 1.5 to 1.7; VSIX and plugin zip inspected by hand.
4. Phase 2 and Phase 3 P1s; Phase 4 API freeze; Phase 5 docs true to the code.
5. Manual account work (1.8) done; all secrets set.
6. Delete `review/`, `prompt.md`, and this file (or move to issues). Squash and merge
   `feature/javafmt-improvements` to `main` (`jreleaser.yml` release config currently expects
   `main`). Re-enable commit signing if it was disabled locally.
7. Tag `v0.1.0`, watch the run, verify `https://central.sonatype.com/artifact/io.javafmt/javafmt-core`,
   the GitHub release assets, both marketplaces.
