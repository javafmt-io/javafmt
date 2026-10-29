# Build, CI, release and repo-hygiene review

## Summary

Not ready to tag. The build and CI side is in good shape — `build.yml` runs the whole
tree (Java tests, oracle suite, IntelliJ tests, Jest, VS Code integration tests, the
Spotless dogfood check) on PRs and main, the version catalog is nearly complete, and
repo hygiene is clean (no secrets, no large binaries, no build output committed). The
release pipeline is the problem: `release.yml` publishes three JARs to Maven Central and
nothing else. No native binary reaches the GitHub release, the IntelliJ plugin is never
signed or published, and the VS Code extension is never packaged or published — despite
`PUBLISHING.md`, `README.md` and `docs/intro.md` all telling users to go get them.

Top three:
1. The GitHub release ships zero binaries; `native.yml` builds them into workflow
   artifacts that nothing collects (`jreleaser.yml` has no
   `distributions`, `.github/workflows/native.yml:99-105`).
2. `release.yml` runs `gradle build` with no Xvfb and no Node setup, while `build.yml`
   needs `xvfb-run` for the same task — the release build will very likely die on
   `:javafmt-vscode:npmTestIntegration`
   (`.github/workflows/release.yml:31` vs
   `.github/workflows/build.yml:39-43`).
3. Marketplace publication does not exist at all: no `publishPlugin`, no `signPlugin`,
   no `vsce publish`, no tokens in `PUBLISHING.md`. Grep across all `*.kts` and
   `*.yml` returns nothing.

## Release pipeline trace

What actually happens on `git push origin v0.1.0`:

1. Two workflows fire independently on the tag: `release.yml` and `native.yml`
   (`.github/workflows/release.yml:3-6`,
   `.github/workflows/native.yml:4-6`). Nothing sequences them.
   **[GAP]** `release.yml` does not `needs:` the native jobs, so JReleaser creates the
   GitHub release while the native matrix is still compiling.

2. `release.yml` checks out with `fetch-depth: 0`, so tags are present
   (`release.yml:15-17`). axion-release reads the `v` prefix
   (`build.gradle.kts:8-12`) and, on a clean detached HEAD
   sitting exactly on `v0.1.0`, resolves `version = 0.1.0` with no `-SNAPSHOT`.
   `javafmt.java-conventions` propagates `rootProject.version` to every JVM module
   (`buildSrc/src/main/kotlin/javafmt.java-conventions.gradle.kts:20`),
   and `javafmt-core` bakes it into a generated `JavafmtVersion` class
   (`javafmt-core/build.gradle.kts:19-44`). This part is sound —
   `-SNAPSHOT` cannot leak into a tagged build.

3. Java 25 (Temurin) and Gradle 9.6.1 are installed (`release.yml:19-28`). **[GAP]** No
   `actions/setup-node`, although `gradle build` drives `:javafmt-vscode:npmBuild`,
   `:javafmt-vscode:npmTest` and `:website:docusaurusBuild`, all of which use the Node
   already on `PATH` (`javafmt.npm-conventions.gradle.kts:8-11`,
   `website/build.gradle.kts:10-12`). It happens to work on the
   GitHub image, at whatever Node version that image ships this month.

4. `gradle build` runs (`release.yml:31`). **[GAP]** No `xvfb-run`. `check` depends on
   `npmTestIntegration` (`javafmt.npm-conventions.gradle.kts:52`), which runs
   `vscode-test` — an Electron launch needing a display on Linux. `build.yml` wraps the
   identical command in `xvfb-run -a` and installs Xvfb first (`build.yml:39-43`).
   The release build very likely fails here.

5. `gradle publishAllPublicationsToStagingRepository` stages signed artifacts into
   `build/staging-deploy` (`release.yml:33-37`). The Gradle `signing` plugin activates
   only when both GPG env vars are non-null
   (`buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:58-65`),
   and both are supplied. Three modules publish: `javafmt-core`, `javafmt-spotless`
   (via `publish-conventions`) and `javafmt-maven-plugin` (its own hand-rolled block,
   `javafmt-maven-plugin/build.gradle.kts:29-73`). POMs carry
   name, description, url, license, developer and scm — Central's required set.
   **[GAP]** `javafmt-cli` applies neither `publish-conventions` nor anything else that
   publishes (`javafmt-cli/build.gradle.kts:3-7`), so its
   shadow JAR exists on disk and goes nowhere.

6. `gradle jreleaserFullRelease` runs (`release.yml:39-47`). Secret names match
   `PUBLISHING.md:50-58` exactly — `JRELEASER_GPG_SECRET_KEY`,
   `JRELEASER_GPG_PUBLIC_KEY`, `JRELEASER_GPG_PASSPHRASE`, `MC_USERNAME`, `MC_TOKEN`.
   This is the one part of the pipeline where the docs and the workflow agree.

7. JReleaser deploys `build/staging-deploy` to `central.sonatype.com`
   (`jreleaser.yml:38-47`) and creates a GitHub release with a
   conventional-commits changelog (`jreleaser.yml:18-31`). **[GAP]** `jreleaser.yml` has
   no `distributions:` and no `files:` block, so the release has release notes and
   source tarballs only. Nothing from step 1's native matrix is attached.

8. `native.yml` meanwhile builds `javafmt` and `javafmt-daemon` for linux-x64,
   darwin-arm64 and win32-x64, smoke-tests them, and uploads them as **workflow
   artifacts** (`native.yml:90-97`, `native.yml:135-142`). Workflow artifacts expire in
   90 days and are not downloadable without a GitHub login. `docs/intro.md:44` tells
   users to "grab a platform binary from GitHub Releases". **[GAP]**

9. **[GAP]** Nothing publishes the IntelliJ plugin. `javafmt-intellij/build.gradle.kts`
   configures `pluginConfiguration` but never `publishing` or `signing`, and no workflow
   calls `publishPlugin`. **[GAP]** Nothing publishes the VS Code extension either —
   `packageVsixTarget` exists (`javafmt-vscode/build.gradle.kts:149-153`)
   but no workflow invokes it, and `package.json` has no `publish` script.

10. **[GAP]** No dry-run path. There is no `workflow_dispatch` on `release.yml` and no
    `-Djreleaser.dry.run=true` anywhere, so the first real execution of this pipeline
    is the first public release.

## Findings

### [P0] GitHub release ships no binaries
- **Where**: `jreleaser.yml:18-31` (no `distributions`/`files`);
  `.github/workflows/native.yml:99-105`;
  `docs/intro.md:44`;
  `PUBLISHING.md:105`
- **What**: `native.yml` uploads the three platform binaries as workflow artifacts and
  explicitly defers attaching them to a release ("belongs to the JReleaser
  `distributions` work tracked as P0.4"). `jreleaser.yml` has no `distributions` block,
  and `release.yml` does not `needs:` the native jobs or download their artifacts.
- **Why it matters for release**: the docs tell people to download a binary from GitHub
  Releases and the release will be empty. The CLI is one of the five advertised
  integrations and it is the one with no other distribution channel at all.
- **Suggested fix**: give `release.yml` a `needs: native` on a reusable-workflow call of
  `native.yml`, `actions/download-artifact` the six binaries into a staging directory,
  and add a `distributions:` (or `files:`) block to `jreleaser.yml` pointing at them.
- **Effort**: M
- **Confidence**: confirmed

### [P0] Release build lacks Xvfb and Node that the identical PR build needs
- **Where**: `.github/workflows/release.yml:19-31`;
  `.github/workflows/build.yml:21-43`;
  `buildSrc/src/main/kotlin/javafmt.npm-conventions.gradle.kts:52`
- **What**: `build.yml` installs Node, installs Xvfb, and runs `xvfb-run -a gradle build`.
  `release.yml` runs bare `gradle build` with neither. `check` depends on
  `npmTestIntegration`, which launches VS Code under Electron.
- **Why it matters for release**: the release workflow fails before it reaches the
  publish steps, or worse, hangs. This is the single command that gates every artifact.
- **Suggested fix**: copy `build.yml`'s Node setup, Xvfb install and `xvfb-run -a`
  wrapper into `release.yml` — or better, factor the build into a reusable workflow both
  call so they cannot drift again.
- **Effort**: S
- **Confidence**: likely (the difference between the two files is confirmed; the failure
  is inferred from `build.yml` needing Xvfb for the same task)

### [P0] IntelliJ plugin is never signed or published
- **Where**: `javafmt-intellij/build.gradle.kts:59-83`;
  `PUBLISHING.md:106`
- **What**: the module configures `pluginConfiguration` but has no
  `intellijPlatform { signing { ... } publishing { ... } }` block, no
  `CERTIFICATE_CHAIN`/`PRIVATE_KEY`/`PRIVATE_KEY_PASSWORD`/`PUBLISH_TOKEN` secrets in
  `PUBLISHING.md`, and no workflow step calling `publishPlugin`. A grep for
  `publishPlugin|signPlugin` across all `*.kts` and `*.yml` returns nothing.
- **Why it matters for release**: `README.md:43` and `docs/intro.md:38-40` tell users to
  search for javafmt in the JetBrains Marketplace. Marketplace uploads also require a
  signed plugin ZIP, which is a separate piece of wiring on top of publishing.
- **Suggested fix**: add `signing` and `publishing` blocks fed from env vars, document
  the four JetBrains secrets in `PUBLISHING.md`, and add a `publishPlugin` step to
  `release.yml` (or a dedicated marketplace workflow gated on the tag).
- **Effort**: M
- **Confidence**: confirmed

### [P0] VS Code extension is never packaged or published on release
- **Where**: `javafmt-vscode/package.json:51-58`;
  `javafmt-vscode/build.gradle.kts:149-153`;
  `.github/workflows/release.yml`
- **What**: `packageVsixTarget` can build a per-platform VSIX but no workflow calls it,
  there is no `vsce publish`/`ovsx publish` script, and no `VSCE_PAT` secret is
  documented. Platform-specific VSIXes also need each native daemon staged into `bin/`
  first, which only `native.yml`'s matrix produces.
- **Why it matters for release**: `docs/intro.md:36` says "Search javafmt in the
  Marketplace". There will be nothing to find.
- **Suggested fix**: add a release job that downloads the `javafmt-daemon-<target>`
  artifacts, runs `packageVsixTarget` per target with `VSCE_TARGET` set, then
  `vsce publish --packagePath` each VSIX with a `VSCE_PAT` secret.
- **Effort**: L
- **Confidence**: confirmed

### [P0] VS Code extension version is hardcoded and will never match the tag
- **Where**: `javafmt-vscode/package.json:5`
- **What**: `"version": "0.1.0"` is a literal. Nothing in `build.gradle.kts` patches it
  from `rootProject.version`, unlike `javafmt-intellij/build.gradle.kts:63` which does.
- **Why it matters for release**: every VSIX ever published would claim 0.1.0, and the
  Marketplace rejects a re-upload at the same version. It also breaks the "one version
  across every integration" promise in `ARCHITECTURE.md:41`.
- **Suggested fix**: add a Gradle task that rewrites `package.json`'s version from
  `project.version` before `packageVsix`/`packageVsixTarget`, or pass
  `vsce package --no-git-tag-version <version>`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No dry-run path for the release pipeline
- **Where**: `.github/workflows/release.yml:3-6`;
  `PUBLISHING.md:60-71`
- **What**: `release.yml` triggers only on `v*` tags. `PUBLISHING.md`'s "Optional —
  dry-run a publish locally" only stages artifacts; it never exercises JReleaser. There
  is no `workflow_dispatch` input and no `-Djreleaser.dry.run=true` anywhere.
- **Why it matters for release**: the first execution of an untested multi-step publish
  is against the real Maven Central and the real GitHub release, with
  `release.github.overwrite: true` set.
- **Suggested fix**: add `workflow_dispatch` with a `dry_run` boolean input to
  `release.yml`, and pass `-Djreleaser.dry.run=${{ inputs.dry_run }}` to the
  `jreleaserFullRelease` step.
- **Effort**: S
- **Confidence**: confirmed

### [P1] PUBLISHING.md's dry-run recipe uses the wrong property names
- **Where**: `PUBLISHING.md:64-67`;
  `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:59-60`
- **What**: the doc tells you to pass `-PJRELEASER_GPG_SECRET_KEY=...` and
  `-PJRELEASER_GPG_PASSPHRASE=...` as Gradle *properties*. The convention plugin reads
  them with `System.getenv`, so the `-P` flags are ignored and the signing block never
  activates — the dry run silently produces unsigned artifacts and looks like it worked.
- **Why it matters for release**: the one pre-flight check in the docs does not check the
  thing it claims to check.
- **Suggested fix**: change the doc to export the two env vars, or make the plugin fall
  back to `providers.gradleProperty(...)` when the env var is absent.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Docs edits never redeploy the website
- **Where**: `.github/workflows/pages.yml:4-8`;
  `website/build.gradle.kts:19`;
  `website/docusaurus.config.ts:34`
- **What**: `pages.yml` triggers only on `website/**` changes, but Docusaurus sources its
  docs from the repo-root `docs/` directory (`path: '../docs'`), which the Gradle task
  also declares as an input. Editing `docs/rules.md` changes the published site's content
  and does not trigger a deploy.
- **Why it matters for release**: `javafmt.io` will silently drift from `docs/` as soon as
  the first post-release doc fix lands.
- **Suggested fix**: add `'docs/**'` to the `paths:` filter in `pages.yml`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No community-health files at all
- **Where**: repo root and `.github/`
- **What**: missing `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  `CHANGELOG.md`, `.github/CODEOWNERS`, `.github/ISSUE_TEMPLATE/`,
  `.github/PULL_REQUEST_TEMPLATE.md`, `.github/FUNDING.yml`. `.github/` contains only
  `dependabot.yml`, `scripts/` and `workflows/`.
- **Why it matters for release**: GitHub shows a "community standards" checklist on every
  public repo. `SECURITY.md` matters most — a formatter that rewrites source has a real
  disclosure surface and there is no stated channel. `CONTRIBUTING.md` matters second,
  because the build has a genuinely unusual prerequisite (SDKMAN, no wrapper) that a
  drive-by contributor will not guess.
- **Suggested fix**: add `SECURITY.md` and `CONTRIBUTING.md` (the latter can mostly lift
  `README.md:12-33` plus the TDD and AssertJ rules from `CLAUDE.md`). `CHANGELOG.md` is
  optional given JReleaser generates release notes; note the absence and move on.
- **Effort**: M
- **Confidence**: confirmed

### [P1] Version catalog is not the single source of truth for build plugins
- **Where**: `buildSrc/build.gradle.kts:10-14`;
  `javafmt-intellij/build.gradle.kts:31`;
  `gradle/libs.versions.toml`
- **What**: `buildSrc/build.gradle.kts` hardcodes five plugin versions as string
  coordinates. Four duplicate catalog entries that must be kept in lockstep by hand
  (errorprone 5.1.0, node 7.0.1, shadow 9.0.0, spotless 8.4.0). The fifth,
  `org.graalvm.buildtools:native-gradle-plugin:0.10.6`, has no catalog entry at all.
  `javafmt-intellij/build.gradle.kts:31` hardcodes `intellijIdeaCommunity("2025.2")`.
  `CLAUDE.md` and `ARCHITECTURE.md:103` both state versions live only in the catalog.
- **Why it matters for release**: a silent skew between `buildSrc`'s Spotless plugin and
  the catalog's `spotlessLib` would change formatter behavior between the dogfood pass
  and the published step. It also makes the stated dependency policy untrue on day one.
- **Suggested fix**: reference the catalog from `buildSrc/build.gradle.kts` via
  `libs.versions.<name>.get()` (the catalog is already wired in
  `buildSrc/settings.gradle.kts:6-10`), add a `graalvmNative` version, and move
  `2025.2` into the catalog as `intellijIdeaCommunity`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Dependabot covers only GitHub Actions
- **Where**: `.github/dependabot.yml:1-6`
- **What**: one `github-actions` entry. No `gradle` ecosystem (root + `buildSrc`), no
  `npm` ecosystem for `javafmt-vscode/` or `website/`, both of which have committed
  lockfiles (`javafmt-vscode/package-lock.json`, `website/package-lock.json`).
- **Why it matters for release**: the npm side is where CVE churn actually lives —
  `@vscode/vsce`, `jest`, `ts-jest`, Docusaurus's transitive tree. A public repo with a
  stale lockfile and visible advisories is a bad first impression.
- **Suggested fix**: add `gradle` entries for `/` and `/buildSrc`, and `npm` entries for
  `/javafmt-vscode` and `/website`, all weekly with grouped minor/patch updates.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Workflows have no `concurrency` group and inconsistent, unpinned action versions
- **Where**: `.github/workflows/build.yml:13-32`;
  `release.yml:15-28`; `native.yml:42-53`; `perf.yml:17-24`; `pages.yml:24-37`
- **What**: only `pages.yml:16-18` declares a `concurrency` group — pushing twice to a PR
  runs the full matrix twice. Action versions are inconsistent across files: `build.yml`
  uses `checkout@v6`/`setup-java@v5`/`setup-gradle@v6` while `release.yml`, `native.yml`,
  `perf.yml` and `pages.yml` all use `@v4`. Nothing is SHA-pinned.
- **Why it matters for release**: a supply-chain compromise of any of these tags reaches
  a workflow holding `contents: write` and the GPG signing key. The version skew also
  means the release path runs on an older, less-tested action set than the path that
  actually gets exercised on every PR.
- **Suggested fix**: add `concurrency: { group: ${{ github.workflow }}-${{ github.ref }},
  cancel-in-progress: true }` to `build.yml` (and `native.yml`); bring all workflows to
  the same major versions; pin at least `release.yml`'s actions to full SHAs.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `build.yml` grants default permissions to the main build job
- **Where**: `.github/workflows/build.yml:8-12` (no `permissions:`)
- **What**: the `build` job has no `permissions:` block, so it inherits the repository's
  default `GITHUB_TOKEN` scope. Only `publish-snapshot` (`build.yml:53-55`) scopes itself.
  `release.yml` has `contents: write` but no other narrowing; `native.yml` and `perf.yml`
  have no `permissions:` at all.
- **Why it matters for release**: `build.yml` runs on `pull_request`, so untrusted code
  executes in the build job. If the repo default is write-all, a malicious PR gets a
  write token.
- **Suggested fix**: add `permissions: contents: read` at the top level of every workflow
  and narrow per-job from there.
- **Effort**: S
- **Confidence**: confirmed

### [P1] npm installs are not reproducible
- **Where**: `buildSrc/src/main/kotlin/javafmt.npm-conventions.gradle.kts:8-11`;
  `website/build.gradle.kts:10-12`;
  `.github/workflows/build.yml:21-24`
- **What**: `node { download.set(false) }` uses whatever Node is on `PATH`, and CI pins
  only `node-version: 'lts/*'` — a moving target. The node-gradle plugin's `npmInstall`
  task defaults to `npm install`, not `npm ci`, so a CI run can resolve differently from
  and even rewrite the committed `package-lock.json`.
- **Why it matters for release**: the VSIX that reaches the Marketplace would not be
  reproducible from the tag.
- **Suggested fix**: set `npmInstallCommand.set("ci")` in both node blocks and pin an
  exact `node-version` in CI (add the version to `libs.versions.toml` or `.nvmrc` so it
  lives in one place).
- **Effort**: S
- **Confidence**: likely (the `npm install` default is from the plugin's documented
  behavior, not verified by running it)

### [P1] IntelliJ plugin will likely be pinned to 2025.2 only
- **Where**: `javafmt-intellij/build.gradle.kts:79-81`
- **What**: `ideaVersion { sinceBuild = "252" }` sets no `untilBuild`. The IntelliJ
  Platform Gradle Plugin 2.x defaults `untilBuild` to the target platform's branch plus
  `.*`, i.e. `252.*`, so the published plugin refuses to install on 2025.3 and later.
- **Why it matters for release**: users on the current IDE at release time may not be
  able to install it, and the Marketplace listing will show a narrow compatibility range.
- **Suggested fix**: set `untilBuild` explicitly — either an open-ended range or
  `untilBuild = provider { null }` — and state the supported range in the description.
- **Effort**: S
- **Confidence**: likely (the plugin's defaulting behavior is documented but not
  confirmed by a build here)

### [P1] VS Code extension has no README, LICENSE or icon
- **Where**: `javafmt-vscode/.vscodeignore:5`;
  `git ls-files javafmt-vscode` (no `README.md`, no `LICENSE`, no icon)
- **What**: `.vscodeignore` un-ignores `LICENSE`, but no such file exists in the module.
  `package.json` declares `"license": "MIT"` with no `LICENSE` beside it, no `icon`, and
  no `README.md` — which is what the Marketplace renders as the whole listing page.
- **Why it matters for release**: the Marketplace page would be blank, and `vsce package`
  prompts on missing README/LICENSE, which is a hazard in a non-interactive CI step.
- **Suggested fix**: add `javafmt-vscode/README.md` and copy the root `LICENSE` into the
  module (a Gradle `Copy` task keeps them from drifting), plus an `icon` entry.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `docs/rules.md` and `CLAUDE.md` both omit two shipping lint rules
- **Where**: `javafmt-core/src/main/java/io/javafmt/lint/LintRules.java:16,20,30`;
  `docs/rules.md:176-196`; `CLAUDE.md`
- **What**: `LintRules.DEFAULTS` registers `RedundantTypeArguments` and
  `ExplodeStarImports`, neither of which appears in `docs/rules.md`'s auto-fix table or
  `CLAUDE.md`'s catalog. `RedundantSuperCall` is in `CLAUDE.md` and in the registry but
  missing from `docs/rules.md`. Separately, `docs/rules.md:44` states a wildcard import
  is "left but emits a warning" and `docs/rules.md:207` explains that expanding one
  "needs a full symbol resolver" — `ExplodeStarImports` does exactly that expansion when
  attribution is available (`ExplodeStarImports.java:41-44` returns empty only when
  `ctx.attribution()` is empty, i.e. without `--analyze`).
- **Why it matters for release**: `docs/rules.md` is described as "the contract" in the
  README, and it under-reports what the tool will do to a user's source. A rule that
  rewrites imports and is not in the published contract is the kind of surprise that
  generates the first angry issue.
- **Suggested fix**: add the three missing rules to `docs/rules.md`'s tables, mark the
  two attribution-dependent ones as active only under `--analyze`, and reword
  `docs/rules.md:44` and `:207` to describe the two-tier wildcard behavior.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Gradle install snippet in `docs/intro.md` is not usable as written
- **Where**: `docs/intro.md:24-32`
- **What**: the Spotless snippet calls
  `io.javafmt.spotless.JavafmtFormatterStep.apply(input)` with no indication of how
  `javafmt-spotless` gets onto the buildscript classpath — no `buildscript` block, no
  dependency coordinates, no version. Every other integration in the file shows
  coordinates (`docs/intro.md:14-20` for Maven).
- **Why it matters for release**: Gradle-via-Spotless is the integration `ROADMAP.md:19-25`
  calls the shortest path to adoption, and it is the one snippet a reader cannot copy.
- **Suggested fix**: show the `buildscript { dependencies { classpath("io.javafmt:javafmt-spotless:<version>") } }`
  (or `plugins`/`spotless` equivalent) alongside the step, and verify the snippet against
  a scratch project before release.
- **Effort**: S
- **Confidence**: confirmed

### [P1] README has no badges, no install snippets and no `javafmt.io` link
- **Where**: `README.md:1-50`
- **What**: the README is a link index. It has no build/Maven Central/licence/marketplace
  badges, no copy-pasteable install for any integration, and never mentions
  `https://javafmt.io` even though `jreleaser.yml:9`, `publish-conventions.gradle.kts:20`
  and `website/docusaurus.config.ts:9` all point there.
- **Why it matters for release**: the README is the landing page for anyone arriving from
  Hacker News or a Maven Central listing. Right now it reads as an internal contributor
  file.
- **Suggested fix**: add a badge row, a "Quick start" block with the Maven and Spotless
  snippets inline, and a link to `https://javafmt.io` above the doc index.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Stray duplicated native-image metadata directory committed
- **Where**: `javafmt-cli/javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/reachability-metadata.json`
- **What**: a nested `javafmt-cli/javafmt-cli/...` path holding a 1924-byte
  `reachability-metadata.json`, committed in a commit titled "save". It is outside every
  source set, so `native-image` never reads it. The real resource directory
  (`javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/`)
  contains only the older-format `reflect-config.json` (123 bytes) and
  `resource-config.json` (232 bytes) plus a `.gitkeep` — no `reachability-metadata.json`.
  This is `metadataCopy --dir javafmt-cli/src/...` run from inside the module directory,
  as `native.yml:14-16` instructs.
- **Why it matters for release**: it is visibly wrong in a public repo, and it suggests
  the CLI's current reachability metadata is not actually being consumed by the native
  build — the binary may be relying on `--no-fallback` luck rather than the captured
  hints.
- **Suggested fix**: delete the nested directory, re-run `metadataCopy` from the repo
  root, and confirm the CLI native build still passes `native.yml`'s smoke test.
- **Effort**: S
- **Confidence**: confirmed (the stray file); the effect on the native build is unverified

### [P2] Website tagline says Java 21+
- **Where**: `website/docusaurus.config.ts:7`
- **What**: `tagline: 'An opinionated, high-performance Java code formatter for modern LTS (Java 21+).'`
  Everywhere else says 25+ (`jreleaser.yml:3`, `README.md:3`, `docs/intro.md:8`,
  `CLAUDE.md`).
- **Why it matters for release**: it is the first line under the title on `javafmt.io`,
  and it promises support for an LTS the toolchain pin (`java-conventions:24`) rules out.
- **Suggested fix**: change 21 to 25.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `perf.yml` has never had a baseline and so never gates
- **Where**: `.github/workflows/perf.yml:33`;
  `.github/scripts/compare-jmh.py:36-38`
- **What**: `.github/perf-baseline.json` is not tracked. The script handles this
  gracefully — it prints the current numbers and returns 0 — so the weekly run passes and
  reports, it just never compares anything.
- **Why it matters for release**: `docs/rubric.md:12` makes a speed claim ("roughly 10 ms
  cold start", "same league" as google-java-format) that nothing in CI currently defends.
- **Suggested fix**: commit a baseline from one clean scheduled run after the first
  release tag.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Native builds are only exercised on tags
- **Where**: `.github/workflows/native.yml:4-6`
- **What**: `native.yml` runs on `workflow_dispatch` and `v*` tags only. Nothing between
  releases checks that GraalVM still compiles the CLI and the daemon.
- **Why it matters for release**: a native-image regression is discovered at tag time,
  when the release is already half-published. `ROADMAP.md:72` and
  `ARCHITECTURE.md:38` both flag native-image as the fragile part of the design.
- **Suggested fix**: add a nightly `schedule:` trigger to `native.yml`. Keeping it off PRs
  is the right call given the compile times.
- **Effort**: S
- **Confidence**: confirmed

### [P2] No Gradle toolchain auto-provisioning for contributors
- **Where**: `settings.gradle.kts:1-14`;
  `.gitignore:5-8`
- **What**: the wrapper is deliberately not committed (SDKMAN does the job per
  `README.md:14`), and `settings.gradle.kts` applies no foojay toolchain resolver. A
  contributor without JDK 25 installed gets a toolchain resolution failure with no
  auto-download path, on top of having no `./gradlew` to fall back on.
- **Why it matters for release**: this is the first thing every external contributor hits.
  The no-wrapper choice is defensible but it is unusual enough that it needs both a
  `CONTRIBUTING.md` and a working fallback.
- **Suggested fix**: add `plugins { id("org.gradle.toolchains.foojay-resolver-convention")
  version "..." }` to `settings.gradle.kts` so Gradle can fetch JDK 25 itself, and
  document the SDKMAN path in `CONTRIBUTING.md`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `javafmt-core` publishes its test-fixtures variant to Maven Central
- **Where**: `javafmt-core/build.gradle.kts:10`;
  `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:16`
- **What**: `java-test-fixtures` adds a test-fixtures variant to the `java` component,
  and `publish-conventions` publishes `from(components["java"])`. The Central artifact set
  will include `javafmt-core-<v>-test-fixtures.jar`, and the Gradle module metadata will
  carry its dependencies.
- **Why it matters for release**: it puts an internal test helper into the public API
  surface permanently, and the module metadata advertises test dependencies on a library
  whose selling point is "minimal runtime dependencies" (`CLAUDE.md`,
  `ARCHITECTURE.md:104`).
- **Suggested fix**: either `components.java { withVariantsFromConfiguration(
  configurations["testFixturesApiElements"]) { skip() } }` (and the runtime one) in
  `javafmt-core`, or decide deliberately that the fixtures are public and document them.
- **Effort**: S
- **Confidence**: likely (the publishing behavior of `java-test-fixtures` is documented;
  the resulting artifact list was not verified by running a publish)

### [P2] `ARCHITECTURE.md` module tree omits two buildSrc files
- **Where**: `ARCHITECTURE.md:70-76`
- **What**: the tree lists four convention plugins but not
  `javafmt.spotless-conventions.gradle.kts` or `JavafmtFormatter.kt`, which together are
  the dogfood mechanism the document spends a paragraph on elsewhere.
- **Why it matters for release**: minor, but `ARCHITECTURE.md` is linked from the README
  as the explanation of how the thing is built.
- **Suggested fix**: add the two entries.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `ROADMAP.md` marks nothing as shipped
- **Where**: `ROADMAP.md:5-66`; `README.md:9`
- **What**: `README.md:9` describes ROADMAP as "what's shipped, what's next, what's still
  open", but the file is seven undifferentiated phase lists with no status markers.
  Phases 1-5 are demonstrably complete (every module exists and is tested); Phase 6
  (configurability) is listed as future while `JavafmtConfig` already exposes `excludes`,
  `verifyOutput` and `lint`; Phase 7's parallel formatting already exists as the CLI's
  `--threads` flag (`docs/intro.md:71`).
- **Why it matters for release**: a public roadmap that does not say what is done reads
  as abandoned or as vapor, and it undersells five completed phases.
- **Suggested fix**: add a status marker per phase and move the delivered Phase 7 items
  into a "shipped" section.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `jreleaser.yml` does not mark 0.x releases as pre-release
- **Where**: `jreleaser.yml:18-22`;
  `docs/rubric.md:22`
- **What**: no `prerelease` configuration. `overwrite: true` is set, which silently
  replaces an existing release for the same tag.
- **Why it matters for release**: `docs/rubric.md:22` says "javafmt will live in
  pre-1.0.0 for a while" — marking 0.x releases as pre-release on GitHub matches that
  and sets expectations. `overwrite: true` is convenient for re-running a failed release
  but will also silently destroy a good release if a tag is re-pushed.
- **Suggested fix**: add `prerelease: { pattern: '.*-.*|0\..*' }` and reconsider
  `overwrite` once the pipeline has a dry-run mode.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Double-signing between Gradle and JReleaser is unverified
- **Where**: `jreleaser.yml:33-36`;
  `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:58-65`;
  `.github/workflows/release.yml:33-47`
- **What**: the Gradle `signing` plugin signs artifacts into `build/staging-deploy`, and
  JReleaser is separately configured with `signing.active: RELEASE, mode: MEMORY` while
  having no `distributions` for that signing to apply to. Whether JReleaser's Maven
  deployer re-signs already-signed staged files, or detects the existing `.asc` files and
  skips, was not confirmed.
- **Why it matters for release**: a duplicated or mismatched signature is a Central
  rejection, discovered only mid-publish.
- **Suggested fix**: resolve it with the dry-run added above — inspect
  `build/staging-deploy` after a full `jreleaserFullRelease -Djreleaser.dry.run=true` and
  confirm exactly one `.asc` per artifact.
- **Effort**: S
- **Confidence**: unverified

### [P2] `prompt.md` deletion is staged but uncommitted
- **Where**: repo root; `git status` shows `D  prompt.md`;
  `.gitignore:47-48` ignores `prompts/` but not `prompt.md`
- **What**: a scratch planning file was tracked and its deletion is staged but not
  committed.
- **Why it matters for release**: it would be visible in the public repo if the deletion
  is not committed before the tag.
- **Suggested fix**: commit the deletion and add `prompt.md` alongside `prompts/` in
  `.gitignore`.
- **Effort**: S
- **Confidence**: confirmed

## Things that are in good shape

Worth recording so they do not get re-reviewed:

- **Version derivation.** axion-release + the `-Pversion` override in
  `build.gradle.kts:8-16` is correct. `-SNAPSHOT` cannot reach a
  tagged build, and `javafmt-core` bakes the same version into `JavafmtVersion` for the
  format cache.
- **Secret naming.** `PUBLISHING.md:50-58`, `release.yml:36-47` and `jreleaser.yml` agree
  on all five secret names. This is the usual place these reviews find a mismatch.
- **POM completeness.** Both publishing paths carry name, description, url, license,
  developers and scm, and `withJavadocJar()`/`withSourcesJar()` are applied — Central's
  full required set.
- **CI coverage.** `build.yml` runs on PRs and main and `gradle build` reaches every
  module's tests: the JUnit suites, the `oracleTest` suite (`javafmt-core/build.gradle.kts:69-71`),
  the IntelliJ tests, the Jest tests and the VS Code integration tests
  (`npm-conventions:52`), plus `spotlessCheck` — the dogfood gate — via the `check`
  lifecycle.
- **Compiler strictness.** `-Xlint:all -Werror` with only `-processing` suppressed, and
  NullAway at `ERROR` for `io.javafmt`
  (`java-conventions:58-65`). Nothing is quietly disabled.
- **Repo hygiene.** `git ls-files` is 681 files; the largest tracked file is
  `website/package-lock.json` at 672 KB. No build output, no `node_modules`, no IDE
  files, no binaries, no secrets. `.claude/settings.json` is three plugin flags and two
  permission rules — nothing private. `.gitignore`, `.gitattributes` and `.editorconfig`
  are all sensible.
- **`native.yml` smoke tests.** The binaries are actually exercised (`--version`, a real
  format, a clean-stderr JSON check) rather than just built — `native.yml:80-88`.
- **`perf.yml` gating.** Weekly schedule, never on PRs, generous thresholds, and a script
  that degrades gracefully. Correctly scoped.

## Coverage notes

**Read in full**: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `jreleaser.yml`, all five workflow files,
`.github/dependabot.yml`, `.github/scripts/compare-jmh.py`, all five buildSrc convention
plugins, `buildSrc/build.gradle.kts`, `buildSrc/settings.gradle.kts`,
`JavafmtFormatter.kt`, `.gitignore`, `.gitattributes`, `.editorconfig`, `.sdkmanrc`,
`.claude/settings.json`, `LICENSE`, `README.md`, `PUBLISHING.md`, `ARCHITECTURE.md`,
`ROADMAP.md`, `docs/intro.md`, all module `build.gradle.kts` files,
`javafmt-vscode/package.json`, `.vscodeignore`, `.vscode-test.mjs`, `jest.config.js`,
`javafmt-intellij/.../plugin.xml`, `LintRules.java`.

**Skimmed**: `docs/rules.md` (read the lint-pass sections and the first 60 lines in full;
the layout-rule prose in the middle was skimmed against `CLAUDE.md`'s bullet list),
`docs/rubric.md` (first 30 lines), `website/docusaurus.config.ts` (first 80 lines),
`ExplodeStarImports.java` (enough to confirm it is attribution-gated).

**Not covered**: `website/` beyond the config file and build script, per the brief. Per-module
application code, per the brief. Commit history, per the brief. I did not run `gradle`, so
every claim about what a task or workflow *does at runtime* — the Xvfb failure, the
`npm install` vs `npm ci` default, the IntelliJ `untilBuild` default, the test-fixtures
variant, and the JReleaser signing interaction — is reasoning from the configuration and
is marked `likely` or `unverified` accordingly. Everything marked `confirmed` was read
directly out of a file.

**Not reached**: whether `maven-plugin-development`'s generated publication actually
carries the sources and javadoc jars (`javafmt-maven-plugin/build.gradle.kts:10-13`
adds them to the `java` component, but which publication consumes that component was not
verified — `PUBLISHING.md:117` already flags this module's publication naming as a known
soft spot, so it deserves a look during the dry run).
