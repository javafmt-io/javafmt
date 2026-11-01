# Javafmt — Project Rules

## What is this project?

An opinionated, high-performance Java code formatter for modern LTS (25+). See `ARCHITECTURE.md` for how it's built and `docs/rules.md` for what it enforces.

## Build commands

- **Always use `gradle`**, never `./gradlew`, never `npm`
- All build operations — including TypeScript compilation and Jest tests — go through Gradle tasks (`gradle :javafmt-vscode:npmBuild`, `gradle :javafmt-vscode:npmTest`, etc.). Never run `npm` directly.

## Code intelligence (Claude Code)

This repo enables three LSP plugins for contributors using Claude Code (`.claude/settings.json`):
`jdtls-lsp` for Java, `kotlin-lsp` for Kotlin (`buildSrc/**/*.kts`, `*.kt`) and `typescript-lsp`
for the VS Code extension (`javafmt-vscode/src/**/*.ts`). One-time setup per contributor: run
`/plugins install jdtls-lsp`, `/plugins install kotlin-lsp` and `/plugins install typescript-lsp`,
then `/reload-plugins`.

Use the LSP tool for symbol-level work: `goToDefinition`, `findReferences`, `goToImplementation`,
`hover` (for types), `documentSymbol`, `workspaceSymbol`, and call hierarchy. Use `rg` only for
text-level searches (string literals, TODO scans, comments). Note: jdtls indexes for ~30–60s on
first use after a clean build.

## Development process

- **TDD is mandatory.** Write a failing test first, then the minimum code to pass, then refactor. No production code without a failing test that motivates it.
- **Never skip tests.** If a test is hard to write, that's a design signal, fix the design, don't skip the test.
- **Commit messages** are single-line [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary): `type: description`. No body, no footer, no `Co-Authored-By` trailer. The author is accountable for every line: Claude drafts commit messages for consistency, but the human reviews every diff and owns the commit. `Co-Authored-By` trailers imply shared authorship and could suggest the human didn't fully own the change.

## Code style (Java)

- **Java 25,** use modern language features everywhere. The one exception is the `javafmt-gradle-plugin` main classes, which target Java 17 so they load in a Gradle running on 17, 21 or 25. The formatter itself runs from the plugin's `engine` source set on a Java 25 toolchain, in a separate worker process. `javafmt-intellij` requires IntelliJ 2026.2, the first release that runs on Java 25.
- **`final var`** for every local where the language allows it. `var` is not allowed for lambdas, method references, `null` or array initializers, and a reassigned local cannot be `final`. Every method and constructor parameter is `final`; lambda parameters never are. javafmt adds the `final`s and some of the `var`s itself (see the lint pass in `docs/rules.md`).
- **Sealed interfaces + records** for all data types
- **Immutability everywhere,** `List.of()`, `Map.of()`, `Set.of()`, unmodifiable collections. No mutable state unless absolutely necessary.
- **Pattern matching** in switch expressions for dispatch
- **No nulls,** `Optional<T>` at API boundaries, `Objects.requireNonNull` at internal boundaries. Every package under `src/main/java` has a `package-info.java` annotated `@NullMarked`. The compile-time check is NullAway (run by Error Prone), which treats all of `io.javafmt` as non-null unless marked `@Nullable`. NullAway is off for tests, benchmarks and the daemon's generated protocol classes, and `javafmt-intellij` does not run Error Prone at all. `Objects.requireNonNull` adds a runtime check on top of NullAway; it does not replace it.
- **No checked exceptions** in public API, wrap in unchecked exceptions. The only exception is a host API that declares one, such as Maven's `MojoExecutionException`.
- **Stream API over mutable accumulators.** Prefer `Stream` pipelines + `.toList()` over `ArrayList` + `for` + `.add()`. Mutable state is a code smell — if you are building a list imperatively, express it as a stream pipeline instead. For nullable results (e.g. `scan()`) in pipelines, use `flatMap(x -> Optional.ofNullable(f(x)).stream())` — this both filters nulls and narrows the element type in one step. The intersperse pattern (separator between elements, not after each) uses `.flatMap(e -> Stream.of(separator, e)).skip(1)`.
- **Lombok,** use `@Getter`/`@Setter` instead of hand-writing accessors; prefer records over `@Data` for data modeling. Lombok is `compileOnly` (annotation processor only, no runtime dependency) in every Java module: through `javafmt.java-conventions`, and declared directly in `javafmt-intellij`, which does not apply those conventions.
- **Logging uses whatever the module's host provides.**
  - `javafmt-core` uses `System.Logger`, so it keeps zero runtime dependencies.
  - The Gradle, Maven and IntelliJ plugins use Gradle's logger, `getLog()` and IntelliJ's `Logger`.
  - `javafmt-daemon` and `javafmt-cli` use SLF4J through Lombok's `@Slf4j`. `javafmt-cli` bundles `slf4j-simple` as `runtimeOnly`, since nothing else provides a binding.

## Testing

- **AssertJ only,** never use JUnit's `assertEquals`, `assertTrue`, `assertNull`, etc. All assertions must use AssertJ's fluent `assertThat(...)` API.
- **Fixture-based tests** for formatting rules: one directory per case under `javafmt-core/src/test/resources/test-fixtures/<case>/`, holding `input.java` and `expected.java`, loaded by `JavafmtFixtureTest`. A case can add more inputs named `input-<variant>.java`; each must format to the same `expected.java`.
- **Idempotent fixtures** (where `format(x) == x`) live as single files at `test-fixtures/idempotent/<name>.java` — no input/expected split. Use this when the contract under test is "this canonical shape is preserved", not "this transformation happens".
- **Unit tests** for internal components (Doc algebra, Printer, individual rules).
- **Idempotency**: `format(format(x))` must equal `format(x)`. Test this.
- **Parameterized tests**: prefer `@ParameterizedTest` over repeated `@Test` methods wherever the same assertion logic runs against multiple inputs.

## Architecture

- **`javafmt-core` has no runtime dependencies.** Adding one needs a strong reason. Test-only dependencies (JUnit 5, AssertJ) are always fine.
- How the modules depend on `javafmt-core`:
  - `javafmt-daemon` (the long-running formatting process and its protobuf protocol), `javafmt-spotless` and `javafmt-maven-plugin` depend on it.
  - `javafmt-cli` depends on it and on `javafmt-daemon`, and is compiled to the native `javafmt` binary.
  - In `javafmt-gradle-plugin`, only the `engine` source set depends on it.
  - `javafmt-intellij` bundles it and formats inside the IDE by default. An IDE-wide setting switches it to the native binary instead, started as `javafmt --daemon` and spoken to over the daemon protocol.
  - `javafmt-vscode` does not depend on it: it always starts the native binary as `javafmt --daemon`.
  - `website` is the documentation site.
- `javafmt-maven-plugin` is a Maven plugin JAR but is built by Gradle via the `org.gradlex.maven-plugin-development` plugin, which generates the `META-INF/maven/plugin.xml` descriptor from `@Mojo` annotations. One build system for the whole monorepo, no `mvn install` bootstrap needed.
- Shared build conventions live in `buildSrc/src/main/kotlin/` (`javafmt.java-conventions`, `javafmt.spotless-conventions`, `javafmt.native-conventions`, `javafmt.npm-conventions`, `javafmt.publish-conventions`), and the settings plugins in `build-settings/`. `javafmt-intellij` applies only the Spotless conventions.
- Every Gradle dependency, Gradle plugin and IntelliJ Platform version goes in `gradle/libs.versions.toml`. npm versions live in each module's `package.json`, and the CI workflows pin their own Java and Gradle versions.

## What the build checks

- Every javac lint warning, Error Prone finding and Javadoc warning fails the build (`-Xlint:all -Werror`, and doclint with `-Werror`).
- Spotless runs javafmt on every Java file under each module's `src/`, tests included, with the `--analyze` rewrites turned on. `gradle build` runs `spotlessCheck`, so run `gradle spotlessApply` before committing. The lint pass rewrites code as well as layout, so expect javafmt to change what you wrote; `docs/rules.md` lists every rewrite.

## Formatting rules (what javafmt enforces)

`docs/rules.md` is the one place the formatting and lint rules are written down. Read it before writing or reviewing Java here, and update it in the same commit as any change to what javafmt produces. Do not copy rules into this file.
