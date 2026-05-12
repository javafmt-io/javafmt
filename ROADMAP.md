# Roadmap

The plan, phase by phase, plus the things still unsettled. For architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Phase 1 — Core library (MVP)

**Goal**: format a single `.java` file from stdin to stdout.

1. Gradle multi-module project (Java 21, Gradle 9.x).
2. `JavaParser` wrapper around `com.sun.source.tree`.
3. `Doc` algebra (sealed interface, record variants).
4. `Printer` (Wadler-Lindig algorithm).
5. `DocBuilder` rules for: package and imports (sorting and grouping), class/interface/enum/record declarations, fields and methods, basic statements (`if`, `for`, `while`, `try`, `switch`), and expressions (method calls, chains, lambdas, ternary).
6. Member ordering.
7. Brace-enforcement lint.
8. Test fixture suite (input/expected pairs).
9. CLI: `java -jar javafmt.jar < Input.java > Output.java`.

## Phase 2 — Spotless integration

**Goal**: use the formatter in any Gradle project via Spotless.

1. `FormatterStep` calling `javafmt-core`.
2. Publish to Maven Central.
3. Dogfood — wire `javafmt-spotless` into javafmt's own build, so every `gradle build` formats javafmt's source with its just-compiled output.

## Phase 3 — Maven plugin

**Goal**: use the formatter in any Maven project, enforced by CI.

1. `javafmt-maven-plugin` with two goals: `javafmt:format` (in place) and `javafmt:check` (CI gate).
2. Plugin embeds `javafmt-core` — no external installation.
3. Publish to Maven Central alongside the core jar.

## Phase 4 — GraalVM native CLI

**Goal**: zero-dependency standalone binary for CI and anyone who isn't on Gradle.

1. GraalVM native-image Gradle plugin on `javafmt-cli`.
2. Run the tracing agent against the test suite to collect reflection and resource hints.
3. Build and test native binaries on Linux, macOS, and Windows (GitHub Actions matrix).
4. If native-image can't handle `com.sun.source.tree`, document the limitation and ship the fat JAR only.

## Phase 5 — Editor integrations

**Goal**: format-on-save in IntelliJ IDEA and VS Code, with output identical to CI.

1. IntelliJ plugin via `ExternalFormatProcessor` (whole-file only; partial ranges fall back to IntelliJ's own formatter).
2. VS Code extension talking NDJSON to a long-lived `javafmt-daemon`, so editor formatting doesn't pay JVM cold-start per request.
3. Both ship platform-native binaries with a fat-JAR fallback.

## Phase 6 — Configurability

**Goal**: let projects override an opinionated default when the case is overwhelming.

- Line width (default: 150)
- Import grouping order
- Member ordering rules
- Single-line threshold behaviors
- Configuration via `.javafmt.toml` or Spotless config

## Phase 7 — Performance and polish

- Parallel file formatting (thread pool).
- Incremental formatting — only changed files, ratcheting style.
- Range formatting (format selection only) where the surrounding context is stable enough.

## Open questions

1. **Enum sorting.** Alphabetical sort can break code if enum constants reference each other (`A(B)` where `B` is defined later). Detect those cases and skip sorting, or always sort and let the compiler catch it?
2. **Member reordering.** Same risk: reordering methods can break forward references in field initializers. Handle carefully or keep opt-in.
3. **GraalVM native-image.** `com.sun.source.tree` uses service loading internally. The tracing agent covers most of it, but new constructs may regress. If this becomes unmaintainable, fat JAR only.
4. **Error recovery.** What does the formatter do with syntactically invalid Java? Return the input unchanged, return partial formatting, or error out. Leaning toward: return input unchanged plus a warning diagnostic.
