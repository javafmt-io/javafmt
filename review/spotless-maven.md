# javafmt-spotless and javafmt-maven-plugin review

## Summary (5 to 10 lines: overall readiness verdict and the top 3 issues)

**Not ready to release as-is.** The Java code in both modules is clean, modern and well-tested at the unit level; the Spotless step in particular is thoughtfully built (serializable state keyed on `JavafmtVersion.VERSION`, `compileOnly` Spotless dep, `NeedsFile` only when needed). The release-blocking problems are in the *packaging* and the *documentation*, not the formatter logic.

Top 3:
1. **`javafmt-maven-plugin` has no Maven publication at all** — `org.gradlex.maven-plugin-development` does not create one, and `javafmt-maven-plugin/build.gradle.kts:32` only *configures* existing publications. `publishAllPublicationsToStagingRepository` will stage nothing for this module and the plugin will never reach Central. (`javafmt-maven-plugin/build.gradle.kts:29-64`)
2. **The documented Gradle snippet is not usable and defeats the step's own up-to-date logic** — `docs/intro.md:26-32` uses `custom("javafmt") { … apply(input) }` with no buildscript classpath wiring, and `custom()` keys Spotless's up-to-date state on `bumpThisNumberIfACustomStepChanges`, not on the javafmt version. The module's own `create()` factory is never documented.
3. **The documented Maven snippet has no `<executions>`** — `docs/intro.md:14-20` adds the plugin but binds nothing, so `mvn verify` runs neither goal. There is also no `skip` parameter and no `sourceEncoding` handling.

Zero integration tests exist for either module (no Gradle TestKit run of Spotless, no maven-invoker IT). Everything is exercised through direct `FormatterStep.format(...)` / `mojo.execute()` calls.

## Findings

### [P0] `javafmt-maven-plugin` is never published — no `MavenPublication` is ever created
- **Where**: `javafmt-maven-plugin/build.gradle.kts:29-64`; `.github/workflows/release.yml:33-34`; `PUBLISHING.md:103-104,117`
- **What**: The module applies `maven-publish` and then does `publishing { publications { withType<MavenPublication>().configureEach { pom { … } } } }`. `configureEach` only touches publications that already exist. Nothing creates one: `javafmt.publish-conventions` (which has the `create<MavenPublication>("mavenJava")` call at `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:15`) is **not** applied to this module, and I confirmed by decompiling the plugin sources that `org.gradlex.maven-plugin-development` 1.0.3 never registers a publication — `MavenPluginDevelopmentPlugin.apply()` only registers `generateMavenPluginHelpMojoSources` / `generateMavenPluginDescriptor` and wires their output into the `jar` task; there is no reference to `PublishingExtension`, `MavenPublication` or `SoftwareComponent` anywhere in the plugin. The signing block at line 71 (`sign(*publishing.publications.toTypedArray())`) therefore also signs an empty array. `PUBLISHING.md:117` documents the opposite belief ("The plugin creates a publication whose exact name varies by version"), which is what the code is written against.
- **Why it matters for release**: The release workflow runs `gradle publishAllPublicationsToStagingRepository` and then hands `build/staging-deploy` to JReleaser. The Maven plugin contributes nothing to that directory, so the release will appear to succeed while `io.javafmt:javafmt-maven-plugin` never appears on Central — the entire Phase 3 deliverable and half of `docs/intro.md` silently does not exist.
- **Suggested fix**: Apply `id("javafmt.publish-conventions")` to `javafmt-maven-plugin/build.gradle.kts` instead of hand-rolling, delete the now-redundant `java { withJavadocJar(); withSourcesJar() }`, `maven-publish`, `signing` and `afterEvaluate` blocks, and add a release-gate assertion (or a staging smoke check) that `build/staging-deploy/io/javafmt/javafmt-maven-plugin/` is non-empty.
- **Effort**: S
- **Confidence**: confirmed

### [P0] The documented Gradle install snippet does not work and silently breaks up-to-date checking
- **Where**: `docs/intro.md:24-32`; `javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java:19-33,86-101`
- **What**: Three separate defects in six lines of documentation.
  (a) The snippet references `io.javafmt.spotless.JavafmtFormatterStep` from `build.gradle.kts` but never shows how to put it on the buildscript classpath (`buildscript { dependencies { classpath("io.javafmt:javafmt-spotless:…") } }` or a `plugins`/`settings` equivalent), and does not show `plugins { id("com.diffplug.spotless") }` either. Copy-pasted as written it fails to compile.
  (b) It calls the *static convenience* `apply(String)` rather than the module's own `FormatterStep` factory `JavafmtFormatterStep.create()`. I decompiled `com.diffplug.gradle.spotless.FormatExtension.custom(String, FormatterFunc)` from `spotless-plugin-gradle` 8.4.0: it builds `FormatterStep.createLazy(name, () -> this.globalState, SerializedFunction.alwaysReturns(func))`, where `globalState` is the integer set by `bumpThisNumberIfACustomStepChanges(int)`. So Spotless's equality/up-to-date key for a `custom` step is a user-maintained integer — **upgrading javafmt will not invalidate anything**. That is precisely the failure mode `JavafmtFormatterStep.State`'s javadoc at lines 86-94 says the version field exists to prevent, and the module's own tests (`JavafmtFormatterStepTest.java:126-144`) assert it works — but no user is told to use that path.
  (c) `com.diffplug.spotless.FormatterFunc` does **not** extend `Serializable` (verified with `javap` against `spotless-lib` 4.5.0), and `custom()` wraps the caller's lambda in `SerializedFunction.alwaysReturns(func)`. A Kotlin lambda passed there is not serializable, which is a hazard under Gradle's configuration cache — unverified end to end, but it is a second reason not to recommend `custom()`.
- **Why it matters for release**: This is the front door for every Gradle user (`website/docusaurus.config.ts:34` serves `../docs`, so `docs/intro.md` *is* the published install page). The public `create()` API — the good one — is documented nowhere in the repo.
- **Suggested fix**: Replace the snippet with a complete, runnable example that puts `io.javafmt:javafmt-spotless:<version>` on the buildscript classpath and uses `spotless { java { addStep(io.javafmt.spotless.JavafmtFormatterStep.create()) } }`. Keep `apply(String)` as an escape hatch but stop leading with it.
- **Effort**: S
- **Confidence**: confirmed

### [P1] The documented Maven snippet binds no executions, so `mvn verify` runs neither goal
- **Where**: `docs/intro.md:14-22`; `javafmt-maven-plugin/src/main/java/io/javafmt/maven/JavafmtMojo.java:15`; `JavafmtCheckMojo.java:15`
- **What**: The `<plugin>` block has no `<executions>`. `@Mojo(defaultPhase = …)` only supplies the phase *if* an execution is declared; declaring the plugin alone binds nothing. The prose then tells users to invoke `mvn io.javafmt:javafmt-maven-plugin:format` by fully-qualified coordinate, even though the generated descriptor sets `<goalPrefix>javafmt</goalPrefix>` (verified in `javafmt-maven-plugin/build/mavenPlugin/plugin-enhanced.xml:12`), so the much shorter `mvn javafmt:format` works once `io.javafmt` is in `pluginGroups` — and `mvn javafmt:check` is the CI gate the docs promise.
- **Why it matters for release**: "Drop it into `pom.xml` and it works" (`ARCHITECTURE.md:39`, `docs/rubric.md:12`) is the plugin's whole pitch, and the documented drop-in does nothing.
- **Suggested fix**: Show an `<executions><execution><goals><goal>check</goal></goals></execution></executions>` example (the CI gate), note that `format` defaults to `process-sources` and `check` to `verify`, and document `mvn javafmt:format` as the direct-invocation form.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Maven plugin has no `skip` parameter
- **Where**: `javafmt-maven-plugin/src/main/java/io/javafmt/maven/AbstractJavafmtMojo.java:28-56` (full parameter list); confirmed absent from the generated descriptor, `javafmt-maven-plugin/build/mavenPlugin/plugin-enhanced.xml:28-63`
- **What**: The only parameters are `threads`, `cache`, `cacheFile`, `excludes` and the readonly `project`. There is no `javafmt.skip` / `skip`, which is the near-universal Maven convention for turning a plugin off per-module or from the command line.
- **Why it matters for release**: Once `check` is bound in a parent POM, a submodule (generated sources, a vendored module, a release build) has no way to opt out short of `<excludes>` gymnastics. Every comparable plugin (`spotless`, `fmt-maven-plugin`, `checkstyle`) has one, and adding it after users have pinned a version is fine but the absence will be the first bug report.
- **Suggested fix**: Add `@Parameter(property = "javafmt.skip", defaultValue = "false") private boolean skip;` to `AbstractJavafmtMojo` and early-return with `getLog().info("javafmt: skipped")` at the top of `execute()`.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Source encoding is hardcoded to UTF-8; `project.build.sourceEncoding` is ignored
- **Where**: `javafmt-maven-plugin/src/main/java/io/javafmt/maven/AbstractJavafmtMojo.java:136-141`; `JavafmtMojo.java:56`
- **What**: `Files.readString(file, StandardCharsets.UTF_8)` and `Files.writeString(file, …, StandardCharsets.UTF_8)`. The mojo never reads `${project.build.sourceEncoding}`. On a project with ISO-8859-1 or Windows-1252 sources, the read throws `MalformedInputException` (an `IOException`), which is wrapped as `UncheckedIOException` at line 139 and surfaces as `MojoExecutionException("javafmt: I/O error: Failed to read <file>")` — a message that does not mention encoding at all.
- **Why it matters for release**: Legacy Maven projects with non-UTF-8 sources are exactly the audience for a formatter. They get a build failure with a misleading message and no parameter to fix it.
- **Suggested fix**: Add `@Parameter(property = "javafmt.encoding", defaultValue = "${project.build.sourceEncoding}") private String encoding;`, default to UTF-8 when unset, and use it for both read and write. At minimum, catch `CharacterCodingException` and say "not valid UTF-8" explicitly.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `check` goal passes on files javafmt cannot parse — the CI gate is silently permeable
- **Where**: `javafmt-maven-plugin/src/main/java/io/javafmt/maven/JavafmtCheckMojo.java:47-53`; test asserting this behavior at `javafmt-maven-plugin/src/test/java/io/javafmt/maven/JavafmtCheckMojoTest.java:78-84`
- **What**: `checkResult` logs parse errors at `warn` level and returns `Optional.empty()`, so an unparseable file never contributes to the failure list. This diverges from the CLI, where `docs/intro.md:84` documents exit code 1 for "a file would be reformatted, **or could not be formatted**". The behavior is deliberate (the test is named `checkGoalSkipsParseErrorFilesWithoutFailing`), but it is inconsistent with the documented contract of the sibling integration.
- **Why it matters for release**: A CI gate that goes green on files the formatter choked on is worse than no gate — a parser regression on a new language construct would hide itself. Users cannot tell the difference between "formatted" and "javafmt gave up" from the exit code.
- **Suggested fix**: Either fail `check` on parse errors (matching the CLI), or add a `failOnParseError` parameter defaulting to `true`, and at minimum raise the log level from `warn` to `error` so it is visible in quiet CI output.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No integration test for either module — nothing runs real Spotless or real Maven
- **Where**: `javafmt-spotless/src/test/java/io/javafmt/spotless/JavafmtFormatterStepTest.java` (the module's only test); `javafmt-maven-plugin/src/test/java/io/javafmt/maven/{JavafmtMojoTest,JavafmtCheckMojoTest,MojoTestSupport}.java` (the module's only tests)
- **What**: Every Spotless test calls `step.format(source, file)` directly; no test constructs a Gradle build and runs `spotlessApply`/`spotlessCheck` through TestKit, so `ratchetFrom`, task up-to-date behavior, configuration cache and the buildscript-classpath story are all unexercised. Every Maven test constructs a mojo with a package-private test constructor and a hand-built `MavenProject` (`MojoTestSupport.java:12-19`); nothing runs `mvn` (no maven-invoker ITs, no `maven-plugin-testing-harness`), so the generated `plugin.xml`, parameter injection from `<configuration>`, `${javafmt.*}` property binding, goal prefix resolution and lifecycle binding have never been executed. I did verify by hand that `toggleOffOn()` composes correctly — running `FenceStep.named(defaultToggleName()).openClose(…).preserveWithin(List.of(JavafmtFormatterStep.create()))` over a fenced source preserved the fenced block verbatim and formatted the rest — but that is one case out of many.
- **Why it matters for release**: Both modules are *only* consumed through the harness that is untested. The P0 publication gap above is exactly the class of defect an integration test catches and a unit test cannot.
- **Suggested fix**: Add a Gradle TestKit `integrationTest` source set in `javafmt-spotless` that applies the Spotless plugin, adds the step, and asserts `spotlessApply` output plus a second up-to-date run; add a maven-invoker (or `maven-plugin-testing-harness`) IT in `javafmt-maven-plugin` that runs `format` and `check` against a fixture POM from the locally staged plugin.
- **Effort**: M
- **Confidence**: confirmed

### [P1] Spotless users never see javafmt's warning-only lint diagnostics
- **Where**: `javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java:54-64,73-84`; compare `javafmt-maven-plugin/src/main/java/io/javafmt/maven/JavafmtMojo.java:52-53`
- **What**: `apply(...)` throws on `result.errors()` and drops `result.warnings()` on the floor. The Maven `format` goal logs them (`JavafmtMojo.java:53`) and the CLI reports them, but the Spotless step neither logs them nor implements `FormatterStep.lint(String, File)` (which `spotless-lib` 4.5.0 provides as a default method, verified with `javap`). So the `FallThrough` / `EqualsHashCode` / `MissingSwitchDefault` / `AvoidStarImport` warnings that `CLAUDE.md` and `docs/rules.md` advertise are invisible to every Gradle user.
- **Why it matters for release**: The ruff-style lint pass is a headline feature (`README.md:3`, `website/src/pages/index.md:36`). Gradle-via-Spotless is the largest target audience and is the one integration where it does nothing.
- **Suggested fix**: Implement `lint(String, File)` on the step (or a custom `FormatterStep` class rather than the `FormatterStep.create` lambda) and map `FormatResult.warnings()` onto `com.diffplug.spotless.Lint`. Failing that, document the gap in `docs/rules.md`.
- **Effort**: M
- **Confidence**: confirmed

### [P2] `javafmt-core` resolves as `runtime` scope in both published POMs, not `compile`
- **Where**: `javafmt-spotless/build.gradle.kts:9`; `javafmt-maven-plugin/build.gradle.kts:16`
- **What**: Both use `implementation(project(":javafmt-core"))` under `java-library`, which Gradle maps to `<scope>runtime</scope>` in the generated POM. For the Maven plugin this is harmless (Maven's plugin realm includes runtime-scope dependencies). For `javafmt-spotless` it means a consumer who wants to *compile* against `io.javafmt.JavafmtConfig` — which the public `create(JavafmtConfig)` overload at `JavafmtFormatterStep.java:23` requires — gets no compile-time access from Maven. Gradle buildscript classpaths resolve runtime, so the common case works.
- **Why it matters for release**: Scopes are baked into published POMs forever; widening later is easy, narrowing is not. The version is correct (project dependency, so both artifacts always carry the exact `javafmt-core` they were built with) — only the scope is off.
- **Suggested fix**: Change `javafmt-spotless` to `api(project(":javafmt-core"))` since `JavafmtConfig` and `FormatterStep` are both in its public signature.
- **Effort**: S
- **Confidence**: confirmed (Gradle scope mapping); the downstream impact is likely

### [P2] No `help` goal and no `HelpMojo` class, though `plugin-help.xml` is shipped
- **Where**: `javafmt-maven-plugin/build.gradle.kts` (no `mavenPlugin { helpMojoPackage.set(...) }` block); generated jar contains `META-INF/maven/io.javafmt/javafmt-maven-plugin/plugin-help.xml` but no `HelpMojo.class`, and `plugin.xml` declares only `<goal>check</goal>` and `<goal>format</goal>`
- **What**: `org.gradlex.maven-plugin-development` registers `generateMavenPluginHelpMojoSources` but gates it with `task.onlyIf(t -> helpMojoPkg.isPresent())` (`MavenPluginDevelopmentPlugin.java:97`), and the extension property is never set. So `mvn javafmt:help` fails with "Could not find goal 'help'" even though the help metadata file is in the jar.
- **Why it matters for release**: Every well-known Maven plugin answers `:help`; its absence is a small but visible sign of a plugin that was not finished.
- **Suggested fix**: Add `mavenPlugin { helpMojoPackage.set("io.javafmt.maven") }` to `javafmt-maven-plugin/build.gradle.kts`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Raw javadoc inline tags leak into the plugin descriptor's parameter help
- **Where**: `javafmt-maven-plugin/build/mavenPlugin/plugin-enhanced.xml:36-38` and the `plugin.xml` inside the jar; source at `AbstractJavafmtMojo.java:37-54`
- **What**: Parameter descriptions render literally, e.g. `Enable incremental processing. When {@code true}, javafmt keeps a small hash-based cache…` and `Defaults to {@code ${project.build.directory}/javafmt.cache}. Only consulted when {@link #cache} is {@code true}.` The XHTML variant is worse: `{@code true}<!-- unsupported tag 'code' -->`.
- **Why it matters for release**: This text is what IDE plugin-configuration completion and `mvn help:describe -Ddetail` show users. It reads as unprocessed source.
- **Suggested fix**: Use plain prose or `<code>`/`<tt>` HTML in the `@Parameter` javadoc rather than `{@code}`/`{@link}` inline tags, and avoid `${…}` literals in descriptions.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Only `build.sourceDirectory` and `build.testSourceDirectory` are walked; extra compile source roots are missed
- **Where**: `javafmt-maven-plugin/src/main/java/io/javafmt/maven/AbstractJavafmtMojo.java:88-90,143-145`
- **What**: `execute()` concatenates `build.getSourceDirectory()` and `build.getTestSourceDirectory()`. Roots added by `build-helper-maven-plugin:add-source` / `add-test-source` live in `project.getCompileSourceRoots()` / `getTestCompileSourceRoots()` and are never visited. (Excluding `target/generated-sources` is the right call; excluding hand-written extra roots is not.) There is also no `includes` parameter — only `excludes` — so there is no way to narrow the set.
- **Why it matters for release**: Multi-source-root projects (a very common Maven shape) will find `check` silently passing on directories it never looked at.
- **Suggested fix**: Walk `project.getCompileSourceRoots()` and `project.getTestCompileSourceRoots()` instead, filtering out anything under `project.getBuild().getDirectory()`; add an `includes` parameter that defaults to everything.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `format` goal writes non-atomically
- **Where**: `javafmt-maven-plugin/src/main/java/io/javafmt/maven/JavafmtMojo.java:54-59`
- **What**: `Files.writeString(file, result.output(), UTF_8)` truncates the target and rewrites in place. An interrupt or disk-full between truncate and write leaves the user's source truncated or empty. (Good news: the guard at line 54 means unchanged files are never rewritten, so there is no mtime churn — `JavafmtMojoTest.java:75-79` asserts this explicitly for the cache path.)
- **Why it matters for release**: A formatter that can destroy source on Ctrl-C is a trust problem, even if the window is small.
- **Suggested fix**: Write to a sibling temp file and `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`, falling back to the direct write if the move is unsupported.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `javafmt.spotless-conventions` resolves `compileClasspath` at configuration time and swallows every failure
- **Where**: `buildSrc/src/main/kotlin/javafmt.spotless-conventions.gradle.kts:65-73`
- **What**: `configurations.named("compileClasspath").get().resolve()` runs during configuration, wrapped in `catch (_: Exception) { emptySet() }`. Configuration-time resolution is incompatible with the Gradle configuration cache and forces dependency resolution on every configuration pass for every JVM module. The blanket catch means that if resolution ever fails the build silently degrades to parse-only formatting — the attribution-dependent lint rules (`RedundantTypeArguments` witness-drop, `LocalVarUseVar` widening) stop running with no message, so the project's own formatted output changes invisibly.
- **Why it matters for release**: This is dogfooding infrastructure rather than a shipped artifact, but a silent, invisible change to the canonical formatting of javafmt's own source is exactly the kind of thing that produces an unexplainable diff on release day.
- **Suggested fix**: Resolve lazily through a `Provider`/`FileCollection` wired into the step, narrow the `catch` to `ResolveException`, and log at `lifecycle` level when the analysis classpath is unavailable.
- **Effort**: M
- **Confidence**: confirmed

### [P2] `javafmt.spotless-conventions` kdoc contradicts the actual wiring
- **Where**: `buildSrc/src/main/kotlin/javafmt.spotless-conventions.gradle.kts:9-11` vs `buildSrc/src/main/kotlin/javafmt.java-conventions.gradle.kts:11-15`
- **What**: The convention plugin's own kdoc says "`:javafmt-spotless` is carved out — it cannot format itself without depending on its own runtime classpath, which is a circular self-reference." There is no carve-out anywhere in the file; `javafmt.java-conventions:15` applies it unconditionally to every JVM module, and its comment at lines 11-14 says the opposite ("`:javafmt-spotless` self-formats: its spotlessApply depends on its own jar … a DAG, not a cycle"). One of the two is stale.
- **Why it matters for release**: `buildSrc` is the first thing an outside contributor reads to understand the dogfooding story, and it currently contradicts itself.
- **Suggested fix**: Delete the stale paragraph from `javafmt.spotless-conventions.gradle.kts` and keep the accurate DAG explanation in `javafmt.java-conventions.gradle.kts`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `JavafmtFormatter.kt` duplicates the step that `javafmt-spotless` already publishes, and only it can reach `applyWithAnalyze`
- **Where**: `buildSrc/src/main/kotlin/JavafmtFormatter.kt:34-118`; `javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java:31,45-48`
- **What**: The build defines a second `FormatterStep` named `"javafmt"` (`JavafmtFormatter.kt:56`) with its own `ClasspathState`, its own SHA-256-based invalidation and its own `URLClassLoader`, calling into `JavafmtFormatterStep` by reflection. The duplication is *justified* — the kdoc at lines 10-33 explains the classloader-isolation reason clearly and it is not a cycle — but two consequences are worth noting. (1) The build's step keys invalidation on jar content hashes while the shipped step keys on `JavafmtVersion.VERSION`, so the two paths have genuinely different cache semantics and a bug in one would not show up in the other. (2) `applyWithAnalyze(String, String)` (`JavafmtFormatterStep.java:45`) is public API reachable *only* through this reflective bridge — no published `create(...)` overload enables analysis-dependent lint, so external Spotless users can never turn those rules on.
- **Why it matters for release**: javafmt formats itself with strictly more lint rules than any external user can enable, which makes the project's own source an unreachable reference standard.
- **Suggested fix**: Add a `create(JavafmtConfig, String classpath)` overload (or a `JavafmtConfig.withAnalysisClasspath(...)`) so the analysis path is part of the published API, and document the limitation in `docs/rules.md` until then.
- **Effort**: M
- **Confidence**: confirmed

### [P2] `javafmt-maven-plugin` publishing config diverges from `javafmt.publish-conventions` in several ways
- **Where**: `javafmt-maven-plugin/build.gradle.kts:33-63` vs `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts:13-56`
- **What**: Even setting aside the P0 above, the hand-rolled block hardcodes `name.set("javafmt-maven-plugin")` instead of `project.name`, omits the `github` Maven Packages repository that every other artifact gets (`publish-conventions:47-54`), and reads the signing key from `System.getenv` in a second place. The POM metadata itself is otherwise complete and Central-compliant for both artifacts: name, description, url (`https://javafmt.io`), MIT license, developer and scm blocks are all present, and `javafmt-spotless` inherits them correctly. Neither POM leaks a test-scope dependency — `javafmt-spotless` declares `spotless-lib` as `compileOnly` (`javafmt-spotless/build.gradle.kts:10`), which correctly keeps it out of the POM so consumers pick their own Spotless version, and the maven plugin keeps `maven-plugin-api`/`maven-core`/`maven-plugin-annotations` `compileOnly` (lines 18-20), which is equivalent to `provided` for descriptor purposes.
- **Why it matters for release**: Two copies of the same metadata will drift; the missing GitHub Packages repo is a small inconsistency in what "published" means per artifact.
- **Suggested fix**: Fold into `javafmt.publish-conventions` as part of the P0 fix.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `FormatterFunc.NeedsFile` path is never tested with a null file
- **Where**: `javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java:78-83`; tests at `JavafmtFormatterStepTest.java:91-123`
- **What**: `buildFunc` returns a `NeedsFile` whenever `excludes` is non-empty and defensively handles `file == null` at line 79. Every excludes test passes a real `File`; the one test that passes `null` (line 122) uses an *empty* excludes list, which takes the plain-func branch. So the null-file branch of the `NeedsFile` lambda has no coverage.
- **Why it matters for release**: Minor. It is a correct-looking guard that nothing verifies, on the path most likely to be hit by a Spotless feature that formats detached content.
- **Suggested fix**: Add a test: `create(defaults().withExcludes(List.of("**/x.java"))).format(src, null)` should format normally.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `@UtilityClass` on an already-`final` class, and mixed static/instance idiom
- **Where**: `javafmt-spotless/src/main/java/io/javafmt/spotless/JavafmtFormatterStep.java:14-17`
- **What**: `@UtilityClass public final class JavafmtFormatterStep` with `private final String NAME = "javafmt"` — Lombok rewrites the field to `static final` and the methods to `static`, so what is written as an instance field is actually a constant. This is idiomatic for this repo (`SourceFiles` and `PathFilter` in `javafmt-core` do the same) but it reads oddly in a *public API* class, where the reader has to know Lombok's rewrite to know that `JavafmtFormatterStep.apply(src)` is the calling convention. The javadoc does not say so, and `docs/intro.md:29` is the only place a caller can see it.
- **Why it matters for release**: Cosmetic; this is the most-read class in the module for anyone wiring it up by hand.
- **Suggested fix**: Leave as is, or add a one-line class javadoc showing `JavafmtFormatterStep.create()` as the entry point.
- **Effort**: S
- **Confidence**: confirmed

## Coverage notes

**Read fully**: `javafmt-spotless/build.gradle.kts`; `javafmt-spotless/src/main/java/io/javafmt/spotless/{JavafmtFormatterStep,package-info}.java`; `javafmt-spotless/src/test/java/io/javafmt/spotless/JavafmtFormatterStepTest.java`; `javafmt-maven-plugin/build.gradle.kts`; all four files under `javafmt-maven-plugin/src/main/java/io/javafmt/maven/`; all three files under `javafmt-maven-plugin/src/test/java/io/javafmt/maven/`; `buildSrc/src/main/kotlin/JavafmtFormatter.kt`; `buildSrc/src/main/kotlin/javafmt.spotless-conventions.gradle.kts`; `buildSrc/src/main/kotlin/javafmt.publish-conventions.gradle.kts`; `CLAUDE.md`; `ARCHITECTURE.md`; `README.md`; `docs/intro.md`; `ROADMAP.md`; `PUBLISHING.md`; `jreleaser.yml`; `settings.gradle.kts`; root `build.gradle.kts`.

**Verified by running code rather than reading it** (no `gradle` invoked, per instructions):
- Decompiled `org.gradlex:maven-plugin-development:1.0.3` sources to confirm it registers no `MavenPublication` and that the help-mojo task is gated on an unset property.
- `javap` on `spotless-lib` 4.5.0 (`FormatterStep`, `SerializedFunction`, `FormatterFunc`, `FenceStep`) and on `spotless-plugin-gradle` 8.4.0 (`FormatExtension.custom`) to establish the step contract and how `custom()` keys its state.
- Ran the built `javafmt-spotless` jar under JDK 25 against `FenceStep.preserveWithin` to confirm `toggleOffOn()` composes correctly with the step (it does; the fenced block is preserved verbatim).
- Inspected the committed build outputs `javafmt-maven-plugin/build/mavenPlugin/plugin-enhanced.xml` and the plugin jar's `META-INF/maven/plugin.xml` for goals, parameters, `threadSafe`, `requiresProject` and phase bindings.

**Skimmed**: `buildSrc/src/main/kotlin/javafmt.java-conventions.gradle.kts` (first 60 lines, enough to establish the spotless-conventions application and dependency wiring); `javafmt-core/src/main/java/io/javafmt/{JavafmtConfig,PathFilter,SourceFiles}.java` (only as far as needed to judge the integrations); `.github/workflows/release.yml` (publish steps only); `docs/rubric.md`, `website/src/pages/index.md` (integration claims only).

**Did not get to / unverified**:
- `ratchetFrom` behavior with the step — it is a Spotless task-level concern and the step looks compatible, but I did not construct a git-backed Gradle build to prove it.
- Gradle configuration-cache serialization of the published step end to end (the state record serializes fine in a unit test at `JavafmtFormatterStepTest.java:126`, but the full Spotless task graph was not round-tripped).
- Whether slf4j binding resolution inside a Maven plugin realm produces a "no providers found" warning at runtime — `javafmt-maven-plugin` declares `slf4j-api` `compileOnly` and relies on Maven's core realm export. Plausible but not tested.
- `javafmt-core`'s own `java-test-fixtures` variant publishing a `-test-fixtures` classifier JAR to Central. Out of scope here (it does not affect either integration's POM) but worth a look by whoever reviews `javafmt-core`.
- A real end-to-end `mvn` run of the plugin against a sample project.
