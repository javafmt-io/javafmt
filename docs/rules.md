---
sidebar_position: 2
title: Formatting rules
---

# Formatting rules

javafmt is opinionated and non-configurable on principle. The rules below are the contract: same input, same output, every version.

## Philosophy

javafmt takes inspiration from the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), `prettier`, and `ruff`: formatting is a contract, and nobody should be arguing about whitespace in code review. But a contract isn't worth much if humans have to fix violations by hand the way Checkstyle forces us to. So javafmt (usually) rewrites the code instead of reporting on it.

Our specific defaults differ from Google's. Each section below notes where and why.

## Line width

**150 characters.**

*Google uses 100. We use 150 so line-level diffs reflect actual changes instead of arbitrary wrap shuffles. Modern monitors and side-by-side diff views handle 150 comfortably.*

## Indentation

- 4 spaces, no tabs.
- Continuation indent: 4 spaces.

*Google uses 2. We use 4 because 2-space nesting visually collapses in wide diffs — you can't see the level you're at without counting.*

## Braces

- K&R style (opening brace on the same line).
- Always required, even for single-statement `if`/`else`/`for`/`while`/`do-while`. Missing braces are auto-added by the `NeedBraces` lint rule.

*Google requires braces but doesn't fail the build on missing ones. We auto-fix to kill the bug class where someone adds a second statement under a braceless `if` and the next reviewer has to verify scope by eye.*

## Imports

- Static imports first (ASCII sort), one blank line, then non-static imports (ASCII sort).
- One flat group. `java.*`, `javax.*`, and third-party all sort together.
- No wildcards. If `import foo.*` appears, the formatter leaves it but emits a warning.

*Same as Google.*

## Member ordering

Order within a class:

1. Fields (public -> protected -> package-private -> private)
2. Constructors
3. Public methods
4. Protected methods
5. Package-private methods
6. Private methods
7. Static methods at the bottom

Within each group, declaration order is preserved. Two carve-outs:

- **Utility classes** — a `final` class with no instance state whose only constructor is a `private` no-arg constructor used to suppress instantiation. That constructor moves to the very bottom of the class, after every method. It's bookkeeping, not behavior.
- **Sealed classes and interfaces** — nested types move to the top of the body, before static fields. The permitted-subtype declarations are the shape of a sealed hierarchy; they're what you want to read first.

Reordering is off by default in IDE integrations (`reorderMembers: false`) because it changes structure, not whitespace. The CLI and Maven plugin apply it unconditionally.

*Google leaves order to the author. We enforce it so reviewers can find the public API without scrolling.*

## Single-line constructs

- `if (cond) { stmt; }` stays on one line if it fits in 150 chars.
- A record whose components fit on one line stays on one line.
- Short lambdas can inline: `list.stream().map(x -> x.name())`.

## Lambdas

Multi-line lambdas get their own lines:

```java
list.stream()
    .filter(item -> {
        return item.isValid();
    })
    .map(item -> item.name())
    .toList();
```

The body indents against the enclosing chain, not the lambda arrow.

## Method chains

- If the chain doesn't fit on one line, break before each `.`.
- Continuation indent is 4 spaces, matching the base indent.

## Enums

- Constants sorted alphabetically.
- Trailing comma after the last constant.
- All constants on one line if they fit in 150 chars; otherwise one per line.

*Google has no sort requirement and treats the trailing comma as optional. We sort alphabetically with a required trailing comma so adding a constant produces a clean one-line diff — no comma juggling on the previous line.*

## Records

If the declaration fits on one line, keep it there:

```java
public record Point(int x, int y) {}
```

Otherwise, one component per line:

```java
public record VeryDetailedRecord(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Optional<String> middleName) {}
```

## Switch expressions

Arrow-style, cases aligned:

```java
return switch (status) {
    case ACTIVE -> handleActive();
    case INACTIVE -> handleInactive();
    case PENDING -> {
        log.info("pending");
        yield handlePending();
    }
};
```

## Annotations

Follows Google's annotation placement:

- **Fields**: inline before the modifiers, space-separated; parameterized annotations are fine:
  ```java
  @Getter @Setter private int age = 10;
  @SuppressWarnings("unchecked") private List<?> items;
  ```
- **Methods**: each annotation on its own line above the signature, except a single parameterless annotation may share the signature line:
  ```java
  @Override public String toString() { ... }   // single + no args: inline

  @SuppressWarnings("all")
  void process() { ... }                       // parameterized: own line

  @Deprecated
  @Override
  void old() { ... }                           // multiple: each own line
  ```

## Javadoc and comments

The standard to beat is google-java-format. Its comment handling is the gold standard: token-precise attachment, layout-time reindentation, and disciplined handling of IDE and tooling directives. javafmt targets parity.

- **Preserved verbatim.** Every `//`, `/* */`, and `/** */` lands in the output with its original text intact. No re-wrapping, no `*`-alignment edits, no stripped content. (Javadoc reflow is a v2 candidate, out of scope for v1.)
- **Token-precise attachment.** Comments attach to source *tokens*, not AST nodes. A comment between `else` and `if`, between two annotation lines, between `,` and the next argument, between `<` and a type parameter, or between `)` and `throws` — they all land in the right place.
- **All positions covered.** File headers above `package`, leading comments on declarations, comments on imports, comments between statements, trailing same-line comments (`int x = 1; // note`), comments inside argument and parameter lists, comments inside empty blocks, comments between the last statement and its closing brace.
- **Layout-time reindentation.** Continuation lines of multi-line block comments are reindented to the comment's *final* output column, not its source column. Move the enclosing declaration and the comment's alignment stays correct.
- **Directive safelist.** IDE and tooling directives stay byte-for-byte and aren't reindented: `// noinspection …`, `// CHECKSTYLE:OFF|ON`, `// SUPPRESS CHECKSTYLE …`, `// @formatter:off|on`, `// NOPMD`, `// $NON-NLS-…$`, and the block-comment equivalents.
- **String / text-block safety.** The scanner only recognises `//` and `/*` outside string literals, character literals, and text blocks.

## Blank lines

- One blank line between methods.
- One blank line between field groups when visibility changes.
- No blank line after the opening brace of a class or method.
- No blank line before the closing brace.
- Never more than one consecutive blank line anywhere — extras collapse.

## Lint pass (safe rewrites)

javafmt enforces [Checkstyle](https://checkstyle.org/)'s rule catalogue the same way [ruff](https://docs.astral.sh/ruff/) enforces pylint and flake8. For each check the question is *"can this be auto-fixed safely?"* — if yes, it's a lint edit; if no, the rule emits a `Diagnostic.Warning` and leaves the source alone.

Lint rules run *before* the formatter, in a fixed-point loop, and produce textual edits.

- **The formatter never breaks code.** A rule emits an edit only when the rewrite is guaranteed to preserve compilation and behavior. If applying it would produce code that doesn't compile (e.g. adding `final` to a parameter the body reassigns), the rule warns instead and the source is left untouched.
- **Edits are unconditional within their safety envelope.** No opt-in fixes — if the rule fires, the edit happens.
- **Convergence is required.** Re-running a rule on its own output must produce zero edits.

### Auto-fix rules

| Rule                          | Checkstyle check                | What javafmt does                                                                                                                                                                                                                                                                                                                                                                                |
|-------------------------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **NeedBraces**                | `NeedBraces`                    | Wraps braceless `if`/`else`/`for`/`while`/`do-while` bodies in `{}`.                                                                                                                                                                                                                                                                                                                              |
| **FinalLocalVariable**        | `FinalLocalVariable`            | Adds `final` to locals that are never reassigned. Reassigned locals are skipped silently.                                                                                                                                                                                                                                                                                                         |
| **FinalParameters**           | `FinalParameters`               | Adds `final` to method and constructor parameters. Reassigned parameters trigger a warning — javafmt won't break compilation.                                                                                                                                                                                                                                                                     |
| **ArrayTrailingComma**        | `ArrayTrailingComma`            | Adds a trailing comma to non-empty array initializers. Empty `{}` initializers are skipped.                                                                                                                                                                                                                                                                                                       |
| **LocalVarUseVar**            | —                               | Replaces the declared type with `var` when the initializer's expression type matches it exactly.                                                                                                                                                                                                                                                                                                  |
| **DefaultComesLast**          | `DefaultComesLast`              | Moves `default` to last position in arrow-form switches. Colon-form is only rewritten when `default` has its own terminating body and isn't part of a label group; otherwise it warns.                                                                                                                                                                                                            |
| **ModifierOrder**             | `ModifierOrder`                 | Reorders modifiers to JLS order: `public protected private abstract default static final transient volatile synchronized native strictfp`.                                                                                                                                                                                                                                                        |
| **RedundantModifier**         | `RedundantModifier`             | Strips modifiers the JLS already implies: `public abstract` on interface methods, `public static final` on interface fields, `static` on nested enum/record/interface declarations, `final` on private methods.                                                                                                                                                                                  |
| **ArrayTypeStyle**            | `ArrayTypeStyle`                | Rewrites C-style array declarations: `String args[]` becomes `String[] args`.                                                                                                                                                                                                                                                                                                                     |
| **UpperEll**                  | `UpperEll`                      | Rewrites lowercase-ell long literals: `1l` becomes `1L`.                                                                                                                                                                                                                                                                                                                                          |
| **MultipleVariableDeclarations** | `MultipleVariableDeclarations` | Splits `int a, b = 0;` into one declaration per line.                                                                                                                                                                                                                                                                                                                                              |
| **UnusedImports**             | `UnusedImports`                 | Removes imports nothing in the file references.                                                                                                                                                                                                                                                                                                                                                   |
| **EmptyStatement**            | `EmptyStatement`                | Drops lone `;` statements.                                                                                                                                                                                                                                                                                                                                                                        |
| **OneStatementPerLine**       | `OneStatementPerLine`           | Splits multiple statements on a single line into separate lines.                                                                                                                                                                                                                                                                                                                                  |
| **NewlineAtEndOfFile**        | `NewlineAtEndOfFile`            | Ensures the file ends with exactly one `\n`.                                                                                                                                                                                                                                                                                                                                                      |
| **ExplicitInitialization**    | `ExplicitInitialization`        | Strips redundant default initialization: `int x = 0;` -> `int x;`, `Object o = null;` -> `Object o;`, `boolean b = false;` -> `boolean b;`.                                                                                                                                                                                                                                                          |

### Warning-only rules

These Checkstyle checks find real problems but can't be auto-fixed without risking compilation or a behavior change. javafmt warns and leaves the source alone.

| Rule                            | Checkstyle check              | Why no auto-fix                                                                                                                                                  |
|---------------------------------|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **FallThrough**                 | `FallThrough`                 | Adding `break;` would change the meaning of intentional fall-through. Mark it as intentional with a `// fallthrough` comment between cases.                      |
| **EqualsHashCode**              | `EqualsHashCode`              | Override both `equals` and `hashCode` or neither. A correct `hashCode` requires knowing which fields identify the value, which is a semantic call.               |
| **MissingSwitchDefault**        | `MissingSwitchDefault`        | What belongs in the default block is up to the author.                                                                                                           |
| **EmptyBlock**                  | `EmptyBlock`                  | Empty `catch`/`finally`/`else` blocks. Filling them needs intent; deleting them might change semantics.                                                          |
| **AvoidStarImport**             | `AvoidStarImport`             | A wildcard import can resolve to more than one candidate. Expanding it safely needs a full symbol resolver.                                                       |
| **HideUtilityClassConstructor** | `HideUtilityClassConstructor` | Adding the private constructor is mechanically safe, but reliably detecting "utility class" without false positives needs type-level analysis.                    |
| **CovariantEquals**             | `CovariantEquals`             | Defining `equals(MyType)` without overriding `equals(Object)` is a bug, but fixing it changes the method signature and may break callers.                         |
| **StringLiteralEquality**       | `StringLiteralEquality`       | `==` used to compare strings. Rewriting to `str.equals(other)` requires confirming the operand type is `String`, which needs a type resolver.                     |

### Out of scope

Detection-only checks with no plausible safe auto-fix — complexity metrics (`CyclomaticComplexity`, `NPathComplexity`), naming conventions (`MethodName`, `TypeName`), magic-number detection, API restriction checks — aren't in the lint pass. Run Checkstyle separately alongside javafmt if you want them.
