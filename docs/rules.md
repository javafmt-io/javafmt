---
sidebar_position: 2
title: Formatting rules
---

# Formatting rules

javafmt is opinionated and non-configurable on principle. The rules below are the contract: same input, same output, every version.

## Philosophy

Formatting is a contract. A reviewer never argues about whitespace. The formatter rewrites code instead of reporting on it.

**No opt-out directives.** `// @formatter:off`, `// spotless:off`, and `// CHECKSTYLE:OFF` do not suspend formatting. The comments stay verbatim. A file that must stay untouched is excluded by path.

## Line width

**150 characters.**

The formatter never splits a string literal or an annotation argument list to meet the width. Those may run past 150.

## Indentation

- 4 spaces, no tabs.
- Continuation indent for call-site argument lists, method chains, `extends`/`implements`/`permits`, and `throws`: 8 spaces.
- A wrapped method or constructor parameter list aligns under the first parameter, one parameter per line:

```java
Invoice buildInvoice(final Customer customer,
                     final List<OrderLine> lines,
                     final TaxPolicy taxPolicy) {
```

- When aligning under the first parameter would push a line past 150 characters, the list wraps at +8 instead, still one parameter per line.
- A wrapped list whose first parameter starts with a `//` comment also wraps at +8, since the comment cannot sit right after `(`.
- A wrapped list that already fits on one line stays on one line.
- Filled argument lists wrap at +8 and pack as many arguments per line as fit.
- `&&`/`||` wrapping indents +4.

## Braces

- K&R: opening brace on the same line.
- Braces are always present on `if`, `else`, `for`, `while`, and `do-while`.
- Loop bodies always expand.
- The compact guard `if (cond) {stmt;}` is kept when it is written that way and fits in 150 characters: no space inside the braces, one statement per branch, no comment, the whole chain on one line. Every other `if` shape expands.
- A multi-line lambda argument may close with `)` or `);` on its own line, at the chain's indent.

## Imports

Three groups, in this order, separated by one blank line:

1. Non-static imports of everything except `java.*` and `javax.*`: `com`, `jakarta`, `lombok`, `org`, and the rest, in one flat ASCII-sorted group.
2. `java.*` and `javax.*`, ASCII-sorted.
3. Static imports, ASCII-sorted, last.

```java
import com.example.billing.Invoice;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.List;
import javax.crypto.Cipher;

import static com.example.billing.Rates.standardRate;
import static java.util.Objects.requireNonNull;
```

- Module imports (`import module M;`) come first, in their own group before the other groups.
- Wildcard imports are left in place with a warning (`AvoidStarImport`), and expanded only by `ExplodeStarImports` under `--analyze`.
- When a file has no `java.*` import, the blank line between groups 1 and 3 stays single.
- Unused imports are removed. Module imports are never removed as unused, because they implicitly bring in many types.

## Line endings and byte-order marks

A file is written back the way it was found: a CRLF file stays CRLF, a BOM is preserved, and a file with mixed line endings is normalised to the ending it uses most. The file ends with exactly one line ending, in the file's own style. Nothing else about the file's bytes is preserved: indentation and trailing whitespace are javafmt's.

## Module declarations

Nothing in a `module-info.java` is reordered: directives stay in the author's order, and so do the target lists of `exports ... to`, `opens ... to` and `provides ... with`. A blank line separates runs of different directive kinds.

## Compact source files

A compact source file (top-level fields and methods without an enclosing class declaration) is formatted with members at column 0, a blank line between members, in declaration order. The implicit class wrapper is never emitted.

## Member ordering

Two moves, no others:

- Nested types (`record`, `class`, `interface`, `enum`, `@interface`) go to the bottom of their enclosing body, in their original order.
- In a utility class, the private no-argument constructor goes last, below the nested types.

Fields, methods, initializer blocks, and enum constants keep the author's order. A moved member takes its Javadoc and comments with it.

## Single-line constructs

- `if (cond) {stmt;}`: see Braces.
- A record whose components fit on one line stays on one line.
- An empty body folds: `) {}` and `private X() {}`.
- Short lambdas inline.

## Lambdas

A multi-line lambda gets its own lines. The body indents 4 past the line that opens it:

```java
orders.stream()
        .filter(order -> {
            final var age = Period.between(order.placedOn(), today);
            return age.getDays() < 30;
        })
        .map(Order::total)
        .forEach(totals::add);
```

Lambda parameters are never `final`.

## Method chains

- Break before each `.` when the chain does not fit in 150 characters, or when the author already put a `.` on its own line. A chain that contains a multi-line text block always breaks.
- Exception: an author-broken chain inside a call's argument list stays broken only when that argument list wraps. When the whole call fits on one line, the chain is joined onto it.
- Continuation indent is 8 spaces past the statement. Not dot-aligned.
- The first call of a chain stays on the line of its receiver (`orders.stream()`, `return Shipments.builder()`).
- `&&`/`||` chains break before every operator, at +4, only when they do not fit in 150 characters or when the author already broke them.

## Enums

- Constants keep declaration order.
- Constants fit on one line, or one per line.
- When the constants do not fit on one line they go one per line, with a trailing comma after the last constant.
- An enum with body declarations keeps its `;`. A `,` written directly before the `;` is removed.

## Records

Fit on one line, keep it there. Otherwise one component per line, aligned under the first component, closing with `) {}` when the body is empty. A record with a body closes the component list with `) {` and the body follows. When aligning under the first component would push a line past 150 characters, the components wrap at +8 instead, still one per line. The same +8 form applies when the first component starts with a `//` comment, since the comment cannot sit right after `(`.

Single-component and small records stay inline: `private record Money(long cents, Currency currency) {}`.

## Switch expressions

Arrow-style, cases aligned. Pattern-matching switches use type patterns, `when` guards, and `case null, default`.

## Annotations

- **Methods and constructors:** every annotation on its own line above the signature, including a lone `@Override`.

```java
@Override
public final Money total() {
```

- **Classes:** every annotation on its own line, in the order the author wrote.
- **Parameters:** annotations inline before `final`: `@Named("primary") final Account account`, `@NonNull final String orderId`, `@Nullable final LocalDate dueDate`.
- **Fields:** annotations stay inline with the field declaration when none of them has arguments and the author wrote them inline (`@Getter @Setter private int count;`). Otherwise each annotation goes on its own line above the field.

## Javadoc and comments

- Preserved verbatim: every `//`, `/* */` and `/** */` keeps its text. No re-wrapping, no `*` realignment, nothing stripped.
- Comments attach to source tokens, not AST nodes. A comment between `else` and `if`, between two annotation lines, between `,` and the next argument, between `<` and a type parameter, or between `)` and `throws` lands in the right place.
- Every position is covered: file headers above `package`, leading comments on declarations, comments on imports, comments between statements, trailing same-line comments, comments inside argument and parameter lists, comments inside empty blocks, and comments between the last statement and its closing brace.
- A comment stays on the line it was written on. A `//` comment after an argument's comma, in a method call or a `new X(...)` expression, after an annotation on its own line (`@Override // why`), on a chain link, or on the `{` that opens an `if` branch keeps that line. A `/* */` comment written beside an argument or parameter stays beside it.
- Continuation lines of a block comment reindent to the comment's final output column, not its source column.
- IDE and tooling directives stay byte-for-byte and are not reindented: `// noinspection ...`, `// CHECKSTYLE:OFF|ON`, `// SUPPRESS CHECKSTYLE ...`, `// @formatter:off|on`, `// NOPMD`, `// $NON-NLS-...$`, and the block-comment equivalents.
- `//` and `/*` are recognised only outside string literals, character literals and text blocks.

## Blank lines

- One blank line around every method, constructor, nested type, and initializer block.
- Between consecutive fields, the author's grouping stands: zero or one blank line. A field carrying Javadoc is separated like a method.
- No blank line after the opening brace of any type, whatever the first member.
- No blank line after a method's opening brace, or before any closing brace.
- Method-body blank lines stay as written.
- One blank line after `package`, and one between import groups.
- Never more than one consecutive blank line.

## Lint pass (safe rewrites)

Lint rules run before the formatter, in a fixed-point loop. A rule emits an edit only when the edit preserves compilation and behavior. Re-running a rule on its own output produces no edits.

### Auto-fix rules

| Rule | What it does |
| ---- | ------------ |
| **NeedBraces** | Wraps braceless `if`/`else`/`for`/`while`/`do-while` bodies in `{}`. |
| **FinalLocalVariable** | Adds `final` to every local never reassigned. Reassigned locals are skipped silently. |
| **FinalParameters** | Adds `final` to every parameter of a method or constructor that has a body. Methods without a body (abstract, native, and non-default interface methods) are left alone, because `final` means nothing there. Lambda parameters stay bare. Reassigned parameters trigger a warning. |
| **FinalCatchParameter** | Adds `final` to every `catch` parameter, after any annotations: `catch (final RuntimeException e)`. Never when the body reassigns it. |
| **FinalForEachVariable** | Adds `final` to the for-each variable: `for (final var x : xs)`. Never when the loop body reassigns it. |
| **LocalVarUseVar** | Replaces the declared type of a local with `var` when the source shows the type stays the same: an unqualified `new` of exactly the declared type, whether or not the local is reassigned; and, for a local that is never reassigned, an unqualified diamond of the declared class, which gains the declared type arguments, a cast to the declared type, or a literal of the declared type. With `--analyze` it also rewrites a factory call that carries the declared type arguments as its witness when javac gives the call exactly the declared type, and a wider declaration such as `List<String> xs = new ArrayList<String>()`, or any other initializer, when javac confirms every use of the local is unchanged. Lambdas, method references, `null` and array initializers keep their declared type, and so does a reassigned local unless its initializer is an unqualified `new` of exactly the declared type. A qualified `new` such as `outer.new Inner()` names a member of the outer object's type, which can differ from the declared `Inner`, so it is rewritten only with `--analyze` and only when every use stays the same. With FinalLocalVariable the result is `final var`. |
| **ArrayTrailingComma** | Adds a trailing comma to non-empty array initializers. Empty `{}` initializers are skipped. |
| **DefaultComesLast** | Moves `default` last in arrow switches. A colon-form switch is rewritten only when `default` has its own terminating body and is not part of a label group; otherwise it warns. |
| **ModifierOrder** | Reorders modifiers to JLS order: `public protected private abstract default static final transient volatile synchronized native strictfp`. |
| **RedundantModifier** | Strips modifiers the JLS implies. |
| **ArrayTypeStyle** | `String[] arguments`, not `String args[]`. |
| **UpperEll** | `1L`, not `1l`. |
| **MultipleVariableDeclarations** | One declaration per statement. |
| **UnusedImports** | Removes unreferenced imports. |
| **EmptyStatement** | Drops lone `;`. |
| **OneStatementPerLine** | Splits multiple statements on one line. |
| **NewlineAtEndOfFile** | Ends the file with exactly one line ending, in the file's own style. |
| **ExplicitInitialization** | Strips redundant default field initializers: `int x = 0;` becomes `int x;`, `Object o = null;` becomes `Object o;`, `boolean b = false;` becomes `boolean b;`. Skipped when the class has a superclass, because the superclass constructor may call an override that assigns the field and `= 0` is then a real write, and when a comment sits inside the text being deleted. |
| **EnumTrailingComma** | Adds a trailing comma after the last constant when the constants do not fit on one line and go one per line. Leaves an enum with body declarations and a `;` unchanged, and removes a `,` written directly before the `;`. |
| **ExplodeStarImports** | Replaces a wildcard import with explicit imports for the members the file references. Only runs with `--analyze`, and leaves every wildcard alone if any name in the file fails to resolve. |
| **RedundantTypeArguments** | Drops explicit type arguments the compiler infers anyway: `new Foo<T>()` becomes `new Foo<>()` when the target type is written identically. With `--analyze` it also drops type witnesses on generic method calls when javac infers the same type without them. |
| **RedundantSuperCall** | Removes a no-argument `super()` as the first statement of a constructor body. |
| **MemberOrder** | Moves nested types to the bottom of their enclosing body and a utility class's private constructor below them. See Member ordering. |
| **UnusedVariableShouldBeUnnamed** | Renames a variable nothing reads to `_`: catch and lambda parameters, enhanced-for and try-with-resources variables, pattern bindings, and locals with an initializer. Fields, method parameters, locals without an initializer and C-style array declarations are left alone. A variable counts as used when any identifier of its name appears in its scope, so shadowing can only stop a rename, never cause a wrong one. Only fires when the source targets Java 22 or later; the integrations read the target release from the build. |

### Warning-only rules

`FallThrough`, `EqualsHashCode`, `MissingSwitchDefault`, `EmptyBlock`, `AvoidStarImport`, `HideUtilityClassConstructor`, `CovariantEquals`, `StringLiteralEquality`.

### Out of scope

Detection-only checks with no safe auto-fix: complexity metrics (`CyclomaticComplexity`, `NPathComplexity`), naming conventions (`MethodName`, `TypeName`), magic-number detection, and API-restriction checks. Run Checkstyle beside the formatter.
