# Javafmt — Project Rules

## What is this project?

An opinionated, high-performance Java code formatter for modern LTS (21+). See `DESIGN.md` for the full architecture and formatting rules.

## Build commands

- **Always use `gradle`**, never `./gradlew`

## Code intelligence (Claude Code)

This repo enables two LSP plugins for contributors using Claude Code (`.claude/settings.json`):
`jdtls-lsp` for Java and `kotlin-lsp` for Kotlin (`buildSrc/**/*.kts`, `*.kt`). One-time setup
per contributor: run `/plugins install jdtls-lsp` and `/plugins install kotlin-lsp`, then
`/reload-plugins`.

Use the LSP tool for symbol-level work: `goToDefinition`, `findReferences`, `goToImplementation`,
`hover` (for types), `documentSymbol`, `workspaceSymbol`, and call hierarchy. Use `rg` only for
text-level searches (string literals, TODO scans, comments). Note: jdtls indexes for ~30–60s on
first use after a clean build.

## Development process

- **TDD is mandatory.** Write a failing test first, then the minimum code to pass, then refactor. No production code without a failing test that motivates it.
- **Never skip tests.** If a test is hard to write, that's a design signal, fix the design, don't skip the test.
- **Commit messages** are single-line [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary): `type: description`. No body, no footer, no `Co-Authored-By` trailer. The author is accountable for every line: Claude drafts commit messages for consistency, but the human reviews every diff and owns the commit. `Co-Authored-By` trailers imply shared authorship and could suggest the human didn't fully own the change.

## Code style (Java)

- **Java 21+,** use modern language features everywhere
- **`final var`** for all local variables; **`final`** on all method parameters unless there is a compelling reason not to
- **Sealed interfaces + records** for all data types
- **Immutability everywhere,** `List.of()`, `Map.of()`, `Set.of()`, unmodifiable collections. No mutable state unless absolutely necessary.
- **Pattern matching** in switch expressions for dispatch
- **No nulls,** `Optional<T>` at API boundaries, `Objects.requireNonNull` at internal boundaries. Every package must have a `package-info.java` annotated `@NullMarked` (JSpecify compile-time enforcement) — `Objects.requireNonNull` is runtime defense-in-depth on top of that, not a replacement.
- **No checked exceptions** in public API, wrap in unchecked exceptions
- **Stream API over mutable accumulators.** Prefer `Stream` pipelines + `.toList()` over `ArrayList` + `for` + `.add()`. Mutable state is a code smell — if you are building a list imperatively, express it as a stream pipeline instead. For nullable results (e.g. `scan()`) in pipelines, use `flatMap(x -> Optional.ofNullable(f(x)).stream())` — this both filters nulls and narrows the element type in one step. The intersperse pattern (separator between elements, not after each) uses `.flatMap(e -> Stream.of(separator, e)).skip(1)`.
- **Lombok,** use `@Slf4j` for logging in all modules; use `@Getter`/`@Setter` instead of hand-writing accessors; prefer records over `@Data` for data modeling. Lombok is `compileOnly` everywhere (annotation processor only, no runtime dep). `slf4j-api` is an `implementation` dep in all modules via `javafmt.java-conventions`; `javafmt-cli` bundles `slf4j-simple` as `runtimeOnly` since it has no host runtime to provide a binding.

## Testing

- **AssertJ only,** never use JUnit's `assertEquals`, `assertTrue`, `assertNull`, etc. All assertions must use AssertJ's fluent `assertThat(...)` API.
- **Fixture-based tests** for formatting rules: `test-fixtures/<category>/input.java` + `expected.java` pairs, loaded by parameterized tests.
- **Idempotent fixtures** (where `format(x) == x`) live as single files at `test-fixtures/idempotent/<name>.java` — no input/expected split. Use this when the contract under test is "this canonical shape is preserved", not "this transformation happens".
- **Unit tests** for internal components (Doc algebra, Printer, individual rules).
- **Idempotency**: `format(format(x))` must equal `format(x)`. Test this.
- **Parameterized tests**: prefer `@ParameterizedTest` over repeated `@Test` methods wherever the same assertion logic runs against multiple inputs.

## Architecture

- **`javafmt-core` has minimal runtime dependencies.** Every dep must be deliberate and well-justified, e.g. `slf4j-api` is acceptable. Test-only deps (JUnit 5, AssertJ) are always fine.
- Integration modules (`javafmt-spotless`, `javafmt-maven-plugin`, `javafmt-cli`, `javafmt-intellij`) depend on `javafmt-core` and add only their integration-specific dependencies.
- `javafmt-maven-plugin` is a Maven plugin JAR but is built by Gradle via the `org.gradlex.maven-plugin-development` plugin, which generates the `META-INF/maven/plugin.xml` descriptor from `@Mojo` annotations. One build system for the whole monorepo, no `mvn install` bootstrap needed.
- Shared build conventions live in `buildSrc/src/main/kotlin/javafmt.java-conventions.gradle.kts`.
- All dependency versions go in `gradle/libs.versions.toml`, nowhere else.

## Formatting rules (what javafmt enforces)

- **150-character line width**
- **K&R braces**, always required (even single-statement `if`/`for`/`while`); missing braces are auto-fixed by the `NeedBraces` lint rule
- **4-space indent**, 4-space continuation indent.
- **Imports**: static imports first (ASCII sort), blank line, then all non-static imports (ASCII sort, one group — no splitting by origin)
- **Member ordering**: static fields, static initializers, instance fields, instance initializers, constructors, public methods, protected, package-private, private, static methods, nested types. Preserve declaration order within groups. Exception: in utility classes (final class, no instance state, only a private no-arg constructor to suppress instantiation), pin the private constructor to the very bottom of the class. Exception: in `sealed` classes and interfaces, nested types are pinned to the top of the body (ahead of static fields), since permitted-subtype declarations are the primary shape of a sealed hierarchy.
- **Enums**: alphabetically sorted constants, trailing comma
- **Records**: one line if fits in 150 chars, otherwise one component per line
- **Lambdas**: Palantir style, multi-line lambdas on their own lines, body indented relative to the enclosing chain
- **Method chains**: break before `.` if chain doesn't fit, 8-space continuation indent
- **Single-line constructs**: `if (cond) { stmt; }` allowed if it fits
- **Annotations**: left alone (v1)
- **Javadoc/comments**: left alone (v1)
- **Blank lines**: one between methods, one between visibility groups, no doubles, no blanks after `{` or before `}`
- **Lint pass**: javafmt applies Checkstyle's rule catalog ruff-style — safe rewrites are auto-fixes, unsafe ones are warnings only. See `DESIGN.md § Safe rewrites` for the full catalog.
  - *Auto-fixes* (unconditional): `NeedBraces`, `FinalLocalVariable`, `FinalParameters`, `ArrayTrailingComma`, `LocalVarUseVar`, `DefaultComesLast`, `ModifierOrder`, `RedundantModifier`, `ArrayTypeStyle`, `UpperEll`, `MultipleVariableDeclarations`, `UnusedImports`, `EmptyStatement`, `OneStatementPerLine`, `NewlineAtEndOfFile`, `ExplicitInitialization`.
  - *Warnings only*: `FallThrough` (suppress with `// fallthrough`), `EqualsHashCode`, `MissingSwitchDefault`, `EmptyBlock`, `AvoidStarImport`, `HideUtilityClassConstructor`, `CovariantEquals`, `StringLiteralEquality`.
