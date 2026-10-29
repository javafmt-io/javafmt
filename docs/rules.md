---
sidebar_position: 2
title: Formatting rules
---

# Formatting rules

javafmt is opinionated and non-configurable on principle. The rules below are the contract: same input, same output, every version.

## Philosophy

javafmt takes inspiration from the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), `prettier`, and `ruff`: formatting is a contract, and nobody should be arguing about whitespace in code review. But a contract isn't worth much if humans have to fix violations by hand the way Checkstyle forces us to. So javafmt (usually) rewrites the code instead of reporting on it.

Our specific defaults differ from Google's. Each section below notes where and why.

**No opt-out directives.** javafmt doesn't honor `// @formatter:off` / `// @formatter:on` (or `// spotless:off`, `// CHECKSTYLE:OFF`, etc.) as skip markers. The comments themselves are preserved verbatim (see "Directive safelist" under Javadoc and comments), but the code between them is reformatted like any other. This is deliberate, and is the same stance [google-java-format](https://github.com/google/google-java-format/issues/137) takes: an opinionated formatter loses its value if any contributor can carve out an exception. If a region truly shouldn't be touched, exclude the whole file via your build's path-exclusion mechanism — `<excludes>` in the Maven plugin, `--exclude` on the CLI, or `targetExclude` in Spotless.

## Line width

**150 characters.**

*Google uses 100. We use 150 so line-level diffs reflect actual changes instead of arbitrary wrap shuffles. Modern monitors and side-by-side diff views handle 150 comfortably.*

## Indentation

- 4 spaces, no tabs.
- Continuation indent: 8 spaces (2× block). Wrapped continuations of a single logical line — method/constructor parameter lists, call-site argument lists, record components, `extends`/`implements`/`permits` and `throws` clauses, and method chains — indent +8 so wrapped content never shares a column with the block body beneath it.
- Logical `&&`/`||` operator wrapping: 4 spaces (matches block indent; these operators sit within a condition expression, not a declaration-level continuation).

*Google uses 2/4. We use 4/8 because 2-space nesting visually collapses in wide diffs — you can't see the level you're at without counting.*

## Braces

- K&R style (opening brace on the same line).
- Always required, even for single-statement `if`/`else`/`for`/`while`/`do-while`. Missing braces are auto-added by the `NeedBraces` lint rule.
- An `if` the author wrote on one line in the compact form — `if (cond) {foo();}`, spaced exactly like that, no space inside the braces, exactly one statement per branch, no comment, the whole chain including every `else` on that line — stays as written, provided it still fits in 150 characters. `if(cond)` or `}else{` is not that form and expands. `if (cond) { foo(); }` with spaces, an `else` on its own line, two statements, or anything `NeedBraces` just rewrote, all expand to the block form.
- Loop bodies always expand, even when the author wrote `for (...) {i++;}` on one line. A loop hides work that deserves its own lines.

*Google requires braces but doesn't fail the build on missing ones. We auto-fix to kill the bug class where someone adds a second statement under a braceless `if` and the next reviewer has to verify scope by eye. The compact one-line `if` is kept because a guard clause is easier to read as one line than as three, and only in that exact shape, because every looser shape is a formatting accident rather than a decision.*

## Imports

- **Module imports** (Java 25 `import module M;`) first (ASCII sort), blank line.
- **Static imports** next (ASCII sort), blank line.
- **Non-static imports** last (ASCII sort). One flat group — `java.*`, `javax.*`, and third-party all sort together.
- No wildcards. If `import foo.*` appears, the formatter leaves it but emits a warning.
- Module imports are never removed as unused — they implicitly bring in many types.

*Static + non-static ordering same as Google. Module imports extend the convention for Java 25.*

## Line endings and byte-order marks

javafmt writes a file back the way it found it: a CRLF file stays CRLF, a BOM is preserved, and a file with mixed endings is normalised to whichever ending it used most. Nothing else about the file's bytes is preserved — indentation, trailing whitespace and the final newline are all javafmt's.

*Rewriting a CRLF file to LF makes every line of it a diff, and whatever javafmt actually changed disappears inside that.*

## Module declarations

Nothing in a `module-info.java` is reordered: directives stay in the author's order, and so do the target lists of `exports ... to`, `opens ... to` and `provides ... with`. A blank line separates runs of different directive kinds.

*Same as google-java-format, and for `provides ... with` it is a correctness requirement: `ServiceLoader` honours declaration order for module-declared providers, so sorting the list changes what `findFirst()` returns.*

## Compact source files

Java 25 compact source files (top-level fields and methods without an enclosing class declaration) are formatted with members at column 0, blank lines between members, in declaration order. The implicit class wrapper is never emitted.

## Member ordering

javafmt makes two moves and no others:

- **Nested types go to the bottom** of the body that declares them — `class`, `interface`, `enum`, `record` and `@interface` alike — keeping their order relative to each other.
- **In a utility class** — a `final` class with no instance state whose only constructor is a `private` no-arg one — that constructor goes last of all, below the nested types.

Everything else stays exactly where the author put it: fields, methods, initializer blocks and enum constants are never reordered, and neither are the members of an anonymous class body or a local class. A moved member takes its Javadoc, its leading comments and its trailing comment with it.

*Both moves are safe in a way general reordering is not: a nested type executes no code and can be referenced from anywhere in the enclosing body, and a constructor whose only job is to prevent instantiation carries nothing a reader needs early. What they buy is a body that opens with what the class does rather than with a page of helper records.*

## Single-line constructs

- `if (cond) {stmt;}` stays on one line if the author wrote it that way and it fits — see Braces above.
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

- Break before each `.` only when the chain does not fit in the 150-character line. A chain that contains a multi-line text block always breaks.
- Continuation indent is 8 spaces past the surrounding context (matching Palantir; not dot-aligned).
- If the author wrote any `.` on its own line in the source, the chain stays multi-line even when it would fit on one.

The same holds for `&&`/`||` chains: they break before every operator only when they do not fit in 150 characters, or when the author already broke them.

## Enums

- Constants kept in declaration order.
- Trailing comma after the last constant.
- All constants on one line if they fit in 150 chars; otherwise one per line.

*Google has no sort requirement and treats the trailing comma as optional. We require the trailing comma so adding a constant produces a clean one-line diff — no comma juggling on the previous line. We do **not** sort constants: `Enum.ordinal()` is a public API, and serialization layers, persistence formats, and switch statements may rely on declaration order. Reordering enum constants is a breaking change for callers, so the formatter leaves them alone.*

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
- **A comment stays on the line it was written on.** A `//` comment after an argument's comma, on a chain link, or on the `{` that opens an `if` branch keeps that line; a `/* */` comment written beside an argument or parameter stays beside it rather than taking a line of its own and breaking the list.
- **Layout-time reindentation.** Continuation lines of multi-line block comments are reindented to the comment's *final* output column, not its source column. Move the enclosing declaration and the comment's alignment stays correct.
- **Directive safelist.** IDE and tooling directives stay byte-for-byte and aren't reindented: `// noinspection …`, `// CHECKSTYLE:OFF|ON`, `// SUPPRESS CHECKSTYLE …`, `// @formatter:off|on`, `// NOPMD`, `// $NON-NLS-…$`, and the block-comment equivalents.
- **String / text-block safety.** The scanner only recognises `//` and `/*` outside string literals, character literals, and text blocks.

## Blank lines

- One blank line around every method, constructor, nested type and initializer block.
- Between consecutive fields, the author's grouping is kept: zero or one blank line, as written. A field carrying Javadoc is separated like a method.
- Inside a method body, the author's blank lines are kept — they are the paragraph breaks that say which statements belong together.
- One blank line after the opening brace of a class, before the first member.
- No blank line after the opening brace of a method, and none before any closing brace.
- One blank line after the `package` declaration.
- Never more than one consecutive blank line anywhere — extras collapse.

*Member separation is google-java-format's rule (`JavaInputAstVisitor.addBodyDeclarations`): javafmt decides where a reader needs air, and leaves the author to decide which fields are a group.*

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
| **LocalVarUseVar**            | —                               | Replaces a local's declared type with `var` when the initializer already says the same thing: a `new` expression of exactly that type, or a static-factory call that already carries a matching type witness. A widening declaration (`List<X> xs = new ArrayList<X>()`) keeps its declared type — `var` would narrow the static type the author chose. Under `--analyze` it also rewrites any initializer javac infers as exactly the declared type, and lifts the declared type arguments onto a same-named static factory (`Optional<Path> x = Optional.empty()` → `var x = Optional.<Path>empty()`) once attribution confirms the method takes that many type parameters.                                                                                       |
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
| **ExplicitInitialization**    | `ExplicitInitialization`        | Strips redundant default initialization from a field: `int x = 0;` -> `int x;`, `Object o = null;` -> `Object o;`, `boolean b = false;` -> `boolean b;`. Skipped when the class has a superclass — field initializers run after the superclass constructor, so if that constructor calls an override which assigns the field, `= 0` is a real write — and when a comment sits inside the text being deleted.                                                                                                                                                                                                                                                          |

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
