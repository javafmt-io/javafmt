---
sidebar_position: 2
title: Formatting rules
---

# Formatting rules

javafmt is opinionated and non-configurable on principle. The rules below are the contract: the same input gives the same output in every version. [Getting started](./intro.md#why-javafmt) explains why it works this way.

## Opt-out directives

`// @formatter:off`, `// spotless:off`, and `// CHECKSTYLE:OFF` do not suspend formatting, and the comments themselves stay verbatim. To keep a file untouched, exclude it by path.

## Line width

Lines are 150 characters wide. The formatter never splits a string literal to meet the width, so a long one may run past 150.

When a declaration's line would run past 150 before the initializer reaches its first possible break, the line breaks after the `=` and the initializer starts on the next line at +8. If the initializer's first line still does not fit there, it stays beside the `=`. An assignment, `=` or a compound operator such as `+=`, breaks after its operator the same way.

## Indentation

- 4 spaces, no tabs.
- A string concatenation split over several lines whose parts are all literals, such as `"alpha" +` above `"beta"`, keeps its later lines where they sit relative to its first line. Tabs at the start of those lines become spaces, counting each tab to the next multiple of 4.
- Continuation indent for call-site argument lists, method chains, `extends`/`implements`/`permits`, and `throws`: 8 spaces.
- A wrapped method or constructor parameter list aligns under the first parameter, one parameter per line:

```java
Invoice buildInvoice(final Customer customer,
                     final List<OrderLine> lines,
                     final TaxPolicy taxPolicy) {
```

- When aligning under the first parameter would push a line past 150 characters, the list wraps at +8 instead, still one parameter per line.
- A wrapped list whose first parameter starts with a `//` comment also wraps at +8, since the comment cannot sit right after `(`.
- A wrapped list whose first parameter has a `//` comment after one of its annotations also wraps at +8, with the comment beside the annotation and the rest of the parameter on the next line.
- A wrapped list that already fits on one line stays on one line.
- Arguments that do not fit on the call's line go on the next line at +8, all on that line when they fit there and one per line otherwise. They go one per line as well when one of them spans several lines.

```java
log.info(
        "Published action notification event. eventId={}, topic={}, messageId={}, tenantId={}, spaceId={}",
        event.getEventId(),
        request.topic(),
        messageId,
        event.getTenantId(),
        event.getSpaceId());
```

- `&&`/`||` wrapping indents +4.

## Braces

- K&R: opening brace on the same line.
- Braces are always present on `if`, `else`, `for`, `while`, and `do-while`.
- Loop bodies always expand.
- The compact guard `if (cond) {stmt;}` is kept when it is written that way and fits in 150 characters: no space inside the braces, one statement per branch, no comment, the whole chain on one line. Every other `if` shape expands.
- A method or constructor whose body the author wrote on one line stays on one line when it fits in 150 characters: `default Message message() {return null;}`. There is no space inside the braces. This applies only when the body is a single statement and the declaration holds no comment. Annotations still go on their own line above it, and blank lines around it follow the usual rule for methods. A body written over several lines, or one that does not fit, expands.
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
- Wildcard imports are left in place with a warning (`AvoidStarImport`), and expanded only by `ExplodeStarImports`, which needs the classpath.
- When a file has no `java.*` import, the blank line between groups 1 and 3 stays single.
- Unused imports are removed. Module imports are never removed as unused, because they implicitly bring in many types.

## Line endings and byte-order marks

A file is written back the way it was found: a CRLF file stays CRLF, a BOM is preserved, and a file with mixed line endings is normalized to the ending it uses most. The file ends with exactly one line ending, in the file's own style. No other bytes are preserved: javafmt decides indentation and trailing whitespace.

## Module declarations

Nothing in a `module-info.java` is reordered: directives stay in the author's order, and so do the target lists of `exports ... to`, `opens ... to` and `provides ... with`. A blank line separates runs of different directive kinds.

## Compact source files

A compact source file (top-level fields and methods without an enclosing class declaration) is formatted with members at column 0, a blank line between members, in declaration order. The implicit class wrapper is never emitted.

## Member ordering

javafmt moves members in two cases only:

- Nested types (`record`, `class`, `interface`, `enum`, `@interface`) go to the bottom of their enclosing body, in their original order.
- In a utility class, the private no-argument constructor goes last, below the nested types.

A JUnit 5 `@Nested` class (written `@Nested` or `@org.junit.jupiter.api.Nested`) keeps its position, because a test class reads top-down. Other nested types in the same body still move below it. A class that declares a `@Nested` class is not a utility class, so its private constructor stays where it is.

Fields, methods, initializer blocks, and enum constants keep the author's order. A moved member takes its Javadoc and comments with it.

## Single-line constructs

- `if (cond) {stmt;}`: see Braces.
- `int size() {return size;}`, written that way: see Braces.
- `x -> {log(x);}`, written that way: see Lambdas.
- A record whose components fit on one line stays on one line.
- An empty body folds: `) {}` and `private X() {}`.
- Short lambdas inline.

## Lambdas

A multi-line lambda gets its own lines. The body indents 4 past the line that opens it.

A block lambda whose body the author wrote on one line stays on one line when it fits in 150 characters, on the same terms as a method: one statement, no comment, and no space inside the braces, as in `orders.forEach(order -> {ship(order);})`. One that does not fit expands like any other block lambda.

A block-bodied lambda that is the last argument of a call stays on the call's line, after the other arguments, and the call closes right after the lambda's `}`:

```java
assertThat(order).satisfies(placed -> {
    assertThat(placed.id()).isEqualTo(1);
});
visits.compute(id, (_, count) -> {
    if (count == null) {
        return 1;
    }
    return count + 1;
});
```

This holds for method calls, `new`, `this(...)`, `super(...)` and enum constant arguments, but only when all of these are true:

- The line up to the lambda's `{` fits in 150 characters.
- The other arguments have no comments, nothing that spans several lines, and no block-bodied lambda.
- In a method chain, the call is the last one, no earlier call spans several lines, and the author did not put a `.` on its own line.

Otherwise the arguments wrap at +8 and the lambda starts its own line. When more calls follow the lambda's call, the chain breaks before every `.`:

```java
orders.stream()
        .filter(order -> {
            final var age = Period.between(order.placedOn(), today);
            return age.getDays() < 30;
        })
        .map(Order::total)
        .forEach(totals::add);
```

A lambda whose body is an expression and that is the last argument of a call starts on the call's line too, when the call does not fit on one line. Its parameters and `->` stay after the other arguments, and its body breaks the way it would anywhere else, at +8 past the line the call starts on:

```java
return clients.stream()
        .map(client -> update(
                api, client, grantsByClient.get(client.getClientId()), REQUIRED_SCOPES, client.getName(), clock.instant(), tenant))
        .toList();
executor.execute(() -> service.process(
        request.tenantId(), request.spaceId(), request.payload(), request.receivedAt(), request.correlationId(), request.source()));
```

This holds when the line up to the body's first break fits in 150 characters and the other arguments have no comments, nothing that spans several lines, and no block-bodied lambda. Otherwise the arguments wrap at +8 with the lambda among them, and so does a lambda that is not the last argument.

When other arguments come before the lambda, it also has to keep its body's leading chain on one line by the column-80 chain rule (see Method chains), and every chain among those other arguments has to meet that rule on the call's line too. If staying on the call's line would split the body's chain or leave another argument's chain past column 80, the arguments wrap at +8 instead, on one line when they fit there and otherwise one per line:

```java
super(
        kafkaTemplate,
        new TopicNameMapper(topicTemplate)::topicByTenant,
        e -> e.value().getEpc());
return entries.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> e.getValue().asText()));
```

A lambda that is a call's only argument stays on the call's line instead, and a body that is a chain of two calls stays on one line with it while the line fits in 150 characters, past column 80 if need be:

```java
oneArgument = someLongReceiverName.transformTheValueWithAVeryLongMethodName(element -> element.value().getEpc());
```

Lambda parameters are never `final`.

## Method chains

- A chain of three or more calls goes on one line, however the author broke it, when it fits in 150 characters and ends by column 80. Otherwise it breaks before each `.`. When its last call takes a block-bodied lambda, the line up to the lambda's `{` has to end by column 80. A chain of two calls only needs the text before its last `.` to end by column 80, so `assertThat(actual).isEqualTo(expected)` stays on one line for longer. The column counts from the start of the line, indentation included, and the rule holds inside another call's arguments too. A two-call chain that is the body of a lambda passed as a call's only argument is exempt while it fits in 150 characters (see Lambdas). So is a two-call chain whose last call is `.stream()`, `.parallelStream()`, `.builder()`, `.newBuilder()` or `.toBuilder()`. Stream and builder chains with a call after that point always break (next rule), and so does a chain that contains a multi-line text block.

```java
log.atInfo()
        .addKeyValue("enabled", isEnabled)
        .log("Received action notification event.");
return tracer.map(Tracer::currentSpan)
        .map(Span::context)
        .map(TraceContext::traceId)
        .filter(ObjectUtils::isNotEmpty)
        .orElse(null);
```

- A chain with a call after its first `.stream()`, `.parallelStream()`, `.builder()`, `.newBuilder()` or `.toBuilder()` always breaks, however short it is. Its first line runs up to and including that call, and each call after it goes on its own line. `Arrays.stream(values)` counts, and so does a chain that starts by creating a builder (see below). When that first line does not fit in 150 characters or ends past column 80, the chain breaks before every `.` instead.

```java
return users.stream()
        .map(User::name)
        .toList();
return audit.toBuilder()
        .skus(skus)
        .build();
```

- A chain whose last call ends in a block-bodied lambda stays on one line up to the lambda's `{` when that fits (see Lambdas). Any other part that spans several lines, such as a block lambda in an earlier call, breaks the chain before every `.`.
- Continuation indent is 8 spaces past the statement. Dots are not aligned with each other.
- When the first line of a broken chain wraps its own arguments, they go at +16, past the calls below them:

```java
return Stream.of(
                new SimpleEntry<>("eventId", event.getEventId()),
                new SimpleEntry<>("metadata", event.hasMetadata() ? event.getMetadata() : null))
        .filter(entry -> !isEmpty(entry.getValue()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
```

- A call that starts its own line in a broken chain keeps its first argument on that line. When the arguments do not fit, the others go on the next line at +8, all on that line when they fit there and one per line otherwise:

```java
log.atLevel(isValid ? Level.INFO : Level.WARN)
        .log("Received action notification event. enabled={}, valid={}, topic={}, key={}, partition={}",
                isEnable, isValid, record.topic(), record.key(), record.partition());
return events.stream()
        .collect(Collectors.groupingBy(
                EventRecord::tenantIdentifier, Collectors.mapping(EventRecord::eventIdentifier, Collectors.toCollection(TreeSet::new))));
```

- The first call of a chain stays on the line of its receiver (`orders.stream()`, `Arrays.stream(values)`, `return Shipments.builder()`). The exception is a chain that starts by creating a builder, a `new` of a class whose name ends in `Builder` such as `new OkHttpClient.Builder()` or `new StringBuilder()`: its first call goes on the next line, and so does each call after it.
- `&&`/`||` chains break before every operator, at +4, only when they do not fit in 150 characters. One that fits goes on one line, however the author broke it.

A chain that is the last argument of a method call or of `new`, and that always breaks (see above) or does not fit on one line even on a line of its own below the call, starts on the call's line. That line holds what the chain's own first line would hold when it breaks (see above), and each later call goes on its own line at +8 past the start of that line:

```java
when(jwtDecoder.decode(VALID_TOKEN))
        .thenReturn(Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "none")
                .claim("sub", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
```

This holds only when the call's line fits in 150 characters, no call follows the call's `)`, the other arguments go on that line with no comments, and the chain has no comments and nothing that spans several lines. A `new` without a class body counts as having no call after it. Otherwise the arguments go on the next line at +8.

## Ternaries

A ternary that fits in 150 characters goes on one line, unless the author broke it: one written with a line break before or after its `?` or `:` stays broken. A broken ternary starts each branch on its own line with `? ` or `: `, at +8 past the line the ternary starts on:

```java
final var toSave = stripClosedEpcs
        ? stripAll(auditReports)
        : auditReports;
```

When the condition is a method chain that breaks, such as a stream, its calls go at +16 so they stand apart from the `?` and `:` lines.

A ternary that is the last argument of a call, and that breaks, starts on the call's line, so its `?` and `:` still sit at +8 past the line the call starts on:

```java
return Optional.of(requested.compareTo(database) >= 0
        ? Direction.ROLLFORWARD
        : Direction.ROLLBACK);
```

This holds when the line up to the condition's first break fits in 150 characters, the other arguments have no comments and nothing that spans several lines, and no chain on that line breaks the column-80 chain rule (see Method chains). A ternary the author wrote on one line first moves to the next line at +8, with the other arguments, when it fits there.

## Enums

- Constants keep declaration order.
- Constants fit on one line, or one per line.
- Constants that fit on one line go on one line, with no trailing comma even if the author wrote one: `enum Size { SMALL, MEDIUM, LARGE }`.
- Constants that go one per line end with a trailing comma after the last constant.
- An enum with body declarations keeps its `;`. A `,` written directly before the `;` is removed.
- A constant's arguments follow the call-site argument rule: when they do not fit they wrap at +8, all on one line when they fit there and one per line otherwise.
- Empty parentheses after a constant, `A()`, stay as written.

## Arrays

- An array initializer that fits in 150 characters goes on one line, with no trailing comma even if the author wrote one: `int[] sizes = {1, 2, 3};`.
- One that does not fit goes one element per line at +8, ends with a trailing comma, and closes with `}` on a line of its own.
- An array creation's `{` sits against its brackets: `new String[]{"a", "b"}`. A comment written between them keeps a space on each side.

## Records

A record that fits on one line stays there. Otherwise it goes one component per line, aligned under the first component, closing with `) {}` when the body is empty. A record with a body closes the component list with `) {` and the body follows. When aligning under the first component would push a line past 150 characters, the components wrap at +8 instead, still one per line. The same +8 form applies when the first component starts with a `//` comment, since the comment cannot sit right after `(`. It also applies when the first component has a `//` comment after one of its annotations; the comment stays beside the annotation.

Single-component and small records stay inline: `private record Money(long cents, Currency currency) {}`.

## Switch expressions

Arrow-style, cases aligned. Pattern-matching switches use type patterns, `when` guards, and `case null, default`.

## Annotations

- Methods and constructors: every annotation written in front of the modifiers goes on its own line above the signature, including a lone `@Override`.

```java
@Override
public final Money total() {
```

- Classes: every annotation on its own line, in the order the author wrote.
- Parameters: annotations inline before `final`: `@Named("primary") final Account account`, `@NonNull final String orderId`, `@Nullable final LocalDate dueDate`.
- Fields: every annotation in front of the modifiers goes on its own line above the field, as on a method.
- Local variables: the annotations in front of the modifiers stay inline with the declaration when none of them has arguments and the author wrote them inline (`@Deprecated final int count = total;`). Otherwise each of them goes on its own line above it.
- An annotation written after the modifiers of a field, local variable, parameter, constructor, or method that returns a value stays after them, beside the type it annotates: `public @Nullable Order find(final String id)`, `private final @Nullable Comparator<Order> order;`, `final @Nullable Order[] orders`. One written after a method's type parameters stays there too: `public static <T> @Nullable T first(final List<T> items)`. A class, interface, enum, record or `void` method has no type for it to annotate, so there it moves in front of the modifiers (see `ModifierOrder`).
- An annotation that fits in 150 characters goes on one line, however the author wrapped it. One that does not ends its first line with `@Name(`, puts each argument on its own line at +8, and closes with `)` right after the last argument. An array value that still does not fit puts one element per line, with `}` on a line of its own:

```java
@IntegrationTest(
        classes = {OrderService.class, OrderConfiguration.class},
        imports = {SecurityConfiguration.class, PersistenceConfiguration.class},
        properties = {"orders.enabled=true", "orders.endpoint=http://localhost:8080/orders"})
class OrderIntegrationTest {
```

- When the only argument is an array, `({` stays on the annotation's line and `})` closes it:

```java
@SuppressWarnings({
        "unchecked", // the cast is checked by the caller
        "rawtypes",
})
```

- When the last argument is an array, as in `name = {...}`, and the annotation does not fit on one line, the first line runs up to that array's `{` if it fits in 150 characters. The elements follow one per line at +8, and `})` closes the annotation on a line of its own:

```java
@SpringSimpleTest(classes = ActionNotificationEventConsumer.class, properties = {
        "radar.event.action-notification.enabled=true",
        "radar.event.action-notification.topic=" + TOPIC,
})
```

- An array inside an annotation follows the rule for every array (see Arrays): a trailing comma when it goes one element per line, none when it fits on one line.
- A `//` comment after an annotation's last argument keeps its line, and `)` goes on the line below it.

## Javadoc and comments

- A comment goes on its own line or at the end of a line. A comment with code both before and after it on the same line, as in `call(/* count */ 3)` or `} else /* otherwise */ {`, is an error: javafmt reports its line and column and leaves the file as written. A comment that starts its line may have code after it, as in `/* note */ int x;`, and javafmt puts it on a line of its own. Another comment may follow one at the end of a line and stays beside it.
- javafmt never writes code after a comment on the same line. A `/* */` comment at the end of a line ends it, just as a `//` comment does.
- Every `//`, `/* */` and `/** */` comment keeps its text verbatim. javafmt does not re-wrap comments, realign their `*` margins, or strip anything from them.
- Comments attach to source tokens, not AST nodes. A comment between `else` and `if`, between two annotation lines, after the `,` of an argument, or between `)` and `throws` stays between the same two tokens in the output.
- Every position is covered: file headers above `package`, comments beside the `package` line and its annotations, leading comments on declarations, comments between two annotations and between the annotations and the rest of a type, method, field or local declaration, comments on imports, comments between statements, trailing same-line comments, comments inside argument and parameter lists, comments inside empty blocks and empty array initializers, comments between the last statement, module directive or array element and the closing brace, comments before the `)` of a `for` or `try` header, and comments before the `;` of a declaration's initializer.
- Any other comment goes back between the same two tokens it was written between. One on its own line stays on its own line before the next token, indented like the code around it. One at the end of a line stays after the token before it and ends the line, and the code after it continues on the next line at the continuation indent. These comments count toward the line width like the code around them, and one inside a list or chain makes it break. A comment never swallows the code after it.
- A comment stays on the line it was written on. A `//` comment after an argument's comma, in a method call, a `new X(...)` expression, an annotation, an array initializer, an enum constant or an `implements`, `permits` or `throws` list, after an annotation on its own line (`@Override // why`) or on a parameter or record component, on a chain link, or on the `{` that opens any block or body keeps that line: a method, constructor, lambda, initializer block, `if` or `else` branch, loop, `try`, `catch`, `finally`, `synchronized`, `switch`, nested block, or class, interface, enum, record, enum constant or anonymous class body. So does a comment after a `case`'s `:` or `->`; after `->` the body follows on the next line. A comment around a binary operator (`&&`, `||`, `+`, `|` and the rest) or a ternary's `?` or `:` stays on its side of the operator: an own-line comment before the operator keeps its own line, a comment at the end of an operand's line keeps that line and breaks the chain before the operator, and a comment at the end of a line after the operator stays after it. A comment after a type's header with the `{` on the next line stays on the header line, after the `{` that javafmt moves up beside it. When several comments sit there, the first goes beside the `{` and the others go on their own lines at the top of the body. A comment at the end of a line after an argument, array initializer element, parameter, enum constant, `for` header part or `try` resource, with the `,` or `;` that follows it on the next line, moves after that separator, so it cannot swallow it. Several comments written there move together, in the order they were written. A comment written after a `,` or `;` in a `for` header stays after it, and the rest of the header continues on the next line at the continuation indent. A comment at the end of a line before any other `,`, `;` or closing `)` keeps its line, and the punctuation moves to the next line. After the last argument of a call or `new`, that `)` goes back to the indentation of the line the call starts on.
- Continuation lines of a block comment reindent to the comment's final output column, not its source column.
- IDE and tooling directives stay byte-for-byte and are not reindented: `// noinspection ...`, `// CHECKSTYLE:OFF|ON`, `// SUPPRESS CHECKSTYLE ...`, `// @formatter:off|on`, `// NOPMD`, `// $NON-NLS-...$`, and the block-comment equivalents.
- `//` and `/*` are recognized only outside string literals, character literals and text blocks.

## Blank lines

- One blank line around every method, constructor, nested type, and initializer block.
- Between consecutive fields, the author's grouping stands: zero or one blank line. A field carrying Javadoc is separated like a method.
- One blank line after the opening brace of every class, interface, enum, record and annotation type whose body is not empty, nested or not. An empty body stays `{}`, an enum whose constants fit on one line stays on one line, and anonymous classes and enum constant bodies get no blank line.
- No blank line after a method's opening brace, or before any closing brace.
- Method-body blank lines stay as written.
- One blank line after `package`, and one between import groups.
- Never more than one consecutive blank line.

## Lint pass (safe rewrites)

Lint rules run before the formatter, in a fixed-point loop. A rule emits an edit only when the edit preserves compilation and behavior. Re-running a rule on its own output produces no edits.

### Auto-fix rules

| Rule | What it does |
| ---- | ------------ |
| `NeedBraces` | Wraps braceless `if`/`else`/`for`/`while`/`do-while` bodies in `{}`. |
| `FinalLocalVariable` | Adds `final` to every local never reassigned, after any annotations: `@SuppressWarnings("unchecked") final List<Order> orders`. Reassigned locals are skipped silently. |
| `FinalParameters` | Adds `final` to every parameter of a method or constructor that has a body, after any annotations. Methods without a body (abstract, native, and non-default interface methods) are left alone, because `final` means nothing there. Lambda parameters stay bare. Reassigned parameters trigger a warning. |
| `FinalCatchParameter` | Adds `final` to every `catch` parameter, after any annotations: `catch (final RuntimeException e)`. Never when the body reassigns it. |
| `FinalForEachVariable` | Adds `final` to the for-each variable: `for (final var x : xs)`. Never when the loop body reassigns it. |
| `LocalVarUseVar` | Replaces the declared type of a local with `var` when the source shows the type stays the same: an unqualified `new` of exactly the declared type, whether or not the local is reassigned; and, for a local that is never reassigned, an unqualified diamond of the declared class, which gains the declared type arguments, a cast to the declared type, or a literal of the declared type. With the classpath it also rewrites a factory call that carries the declared type arguments as its witness when javac gives the call exactly the declared type, and a wider declaration such as `List<String> xs = new ArrayList<String>()`, or any other initializer, when javac confirms every use of the local is unchanged. Lambdas, method references, `null` and array initializers keep their declared type, and so does a reassigned local unless its initializer is an unqualified `new` of exactly the declared type. A qualified `new` such as `outer.new Inner()` names a member of the outer object's type, which can differ from the declared `Inner`, so it is rewritten only with the classpath and only when every use stays the same. With FinalLocalVariable the result is `final var`. |
| `ArrayTrailingComma` | Adds a trailing comma to every non-empty array initializer, inside annotations too. It is printed only when the array goes one element per line (see Arrays). |
| `DefaultComesLast` | Moves `default` last in arrow switches. A colon-form switch is rewritten only when `default` has its own terminating body and is not part of a label group; otherwise it warns. |
| `ModifierOrder` | Reorders modifiers to JLS order: `public protected private abstract default static final transient volatile synchronized native strictfp`. Annotations in front of the keywords stay there. An annotation written among or after the keywords goes after them on a field, variable, parameter, constructor, or method that returns a value, and in front of them on a class, interface, enum, record, or `void` method. |
| `RedundantModifier` | Strips modifiers the JLS implies. |
| `ArrayTypeStyle` | `String[] arguments`, not `String args[]`. A comment written among the brackets after the name, as in `byte buffer[/* size */]`, moves after the name. |
| `UpperEll` | `1L`, not `1l`. |
| `MultipleVariableDeclarations` | One declaration per statement, in a block, a `case` group or a class body. A comment among the shared annotations, modifiers and type stays with the first declaration. |
| `UnusedImports` | Removes unreferenced imports. |
| `EmptyStatement` | Drops lone `;`, including the optional one after the last resource of a `try`: `try (var in = open();)` becomes `try (var in = open())`. |
| `OneStatementPerLine` | Splits multiple statements on one line. A comment between two of them stays at the end of the first one's line. |
| `NewlineAtEndOfFile` | Ends the file with exactly one line ending, in the file's own style. |
| `ExplicitInitialization` | Strips redundant default field initializers: `int x = 0;` becomes `int x;`, `Object o = null;` becomes `Object o;`, `boolean b = false;` becomes `boolean b;`. Skipped when the class has a superclass, because the superclass constructor may call an override that assigns the field and `= 0` is then a real write, and when a comment sits inside the text being deleted. |
| `EnumTrailingComma` | Adds a trailing comma after the last constant. It is printed only when the constants go one per line (see Enums). Leaves an enum with body declarations and a `;` unchanged, and removes a `,` written directly before the `;`. |
| `ExplodeStarImports` | Replaces a wildcard import with explicit imports for the members the file references. Needs the classpath, and leaves every wildcard alone if any name in the file fails to resolve. |
| `RedundantTypeArguments` | Drops explicit type arguments the compiler infers anyway: `new Foo<T>()` becomes `new Foo<>()` when the target type is written identically, apart from spacing and comments. With the classpath it also drops type witnesses on generic method calls when javac infers the same type without them. Type arguments that hold a comment are kept, and so is a witness that carries a type annotation, such as `Stream.<@Nullable T>iterate(...)`. |
| `RedundantSuperCall` | Removes a no-argument `super()` as the first statement of a constructor body. A comment written inside its parentheses stays where the call was. |
| `MemberOrder` | Moves nested types other than JUnit `@Nested` classes to the bottom of their enclosing body and a utility class's private constructor below them. See Member ordering. |
| `UnusedVariableShouldBeUnnamed` | Renames a variable nothing reads to `_`: catch and lambda parameters, enhanced-for and try-with-resources variables, pattern bindings, and locals with an initializer. Fields, method parameters, locals without an initializer and C-style array declarations are left alone. A variable counts as used when any identifier of its name appears in its scope, so shadowing can only stop a rename, never cause a wrong one. Only fires when the source targets Java 22 or later; the integrations read the target release from the build. |

### Warning-only rules

`FallThrough`, `EqualsHashCode`, `MissingSwitchDefault`, `EmptyBlock`, `AvoidStarImport`, `HideUtilityClassConstructor`, `CovariantEquals`, `StringLiteralEquality`.

### Rules that need the classpath

`ExplodeStarImports`, and the parts of `LocalVarUseVar` and `RedundantTypeArguments` described above as needing the classpath, use javac's resolved types. They run whenever the integration knows the compile classpath of the file: the CLI with `--classpath`, the Gradle plugin by default, the Spotless step when it is given one, the IntelliJ plugin for every file in a module, and the VS Code extension when Red Hat's Java extension gives it the classpath (see [Getting started](./intro.md)). Without a classpath, or with `--no-analyze` or `analyze = false`, they are skipped and every other rule runs as usual.

The classpath may be incomplete or out of date. A rewrite that depends on a name javac cannot resolve is not made, and a file javac gives up on because of such a name is formatted without these rules. If javac fails on a file for any other reason, or formatting it with the classpath fails, the file is formatted without these rules and a warning says why.

Besides the classpath, a name resolves from the other files formatted in the same run. javafmt attributes a run in batches of up to 128 files, taken in order and cut where the directory changes when it can, and halves a batch with more than 4 MB of source, so its memory stays bounded however many files it formats; a name declared in a file of another batch resolves only through the classpath. The batches depend on the files and not on what formatting does to them, so formatting the same files again changes nothing, and a run formats them the same way on every machine. When a rewrite makes another one possible, javafmt attributes the rewritten files again before it finishes.

A `.javafmt` file can turn any of these rules off for the files below it:

```properties
analyze.skip=ExplodeStarImports, LocalVarUseVar
```

A skipped rule behaves exactly as it does without a classpath, so the parts of `LocalVarUseVar` and `RedundantTypeArguments` that need no classpath keep running. javafmt reads the first `.javafmt` it finds walking up from the formatted file's directory, and stops at the directory that contains `.git`. `analyze.skip` is the only setting. An unknown setting, or a name that is not one of the three rules above, is an error: javafmt leaves every file the project file applies to unchanged and says which name is wrong.

### Out of scope

Detection-only checks with no safe auto-fix: complexity metrics (`CyclomaticComplexity`, `NPathComplexity`), naming conventions (`MethodName`, `TypeName`), magic-number detection, and API-restriction checks. Run Checkstyle beside the formatter.
