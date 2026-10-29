# javafmt-core formatting engine review

## Summary

Not ready for a public release. The Doc algebra and printer are clean, well-documented and well
tested; the AST→Doc builder is where the gaps are, and they are not edge cases. The single worst
one is that **no type declaration renders its type parameters** — `class Box<T>` comes out as
`class Box` — which means essentially every generic class, interface and record in a real codebase
is either corrupted (verification off) or silently refused (verification on, the default) with an
`InternalError` the CLI turns into a non-zero exit. A corpus run over JDK 25 `java.base` sources
hit an `InternalError` on **51 of the first 443 files (12%)**, dominated by that bug plus multi-
variable `for` initializers, stray `;` members and the enum trailing comma. A further 13 files
(3%) came out with lines over the advertised 150-char limit.

Top three:
1. **P0** Type parameters dropped from class/interface/record declarations —
   `builder/ClassLikeRenderer.java:20`, `builder/RecordRenderer.java:18`.
2. **P0** `for (int i = 0, len = n; ...)` renders as `for (int i = 0, int len = n; ...)` —
   `builder/LoopRenderer.java:25` + `builder/FieldRenderer.java:14`.
3. **P0/P1** The documented enum trailing comma is rejected by the round-trip verifier, so any
   enum that has to wrap is frozen unformatted — `builder/EnumRenderer.java:87`.

The round-trip verifier is the saving grace: it converts most of these from corruption into
"file silently not formatted". It also has a blind spot (multiset comparison, operators lexed one
char at a time) that lets `- -x` → `--x` through. Beyond correctness, the advertised 150-char line
width is not actually enforced for array initializers, constructor arguments, annotation
arguments, type arguments or try-with-resources headers.

All findings below were reproduced by running the compiled `javafmt-core` classes from
`javafmt-core/build/classes/java/main` against handmade inputs and against JDK 25 `src.zip`
sources. No repository file was modified.

## Findings

### [P0] Class, interface and record declarations drop their type parameters
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ClassLikeRenderer.java:20,25`;
  `javafmt-core/src/main/java/io/javafmt/builder/RecordRenderer.java:18,41`
- **What**: the header is built as
  `ModifierRenderer.modifiersText(mods) + keyword + " " + node.getSimpleName()`.
  `ClassTree.getTypeParameters()` is never read anywhere in the builder (only
  `MethodRenderer.buildLeading` renders *method* type parameters). So
  `public class Box<T extends Comparable<T>>` renders as `public class Box`, and
  `public record Pair<A, B>(A a, B b)` as `public record Pair(A a, B b)`.
  Reproduced: three-line inputs for a generic class, generic interface and generic record all
  return `InternalError[... output dropped `<`, `extends`, `Comparable`, `<`, `>` and 3 more ...]`
  and the source is handed back unformatted. With `JavafmtConfig.withVerifyOutput(false)` the
  output is emitted and does not compile.
- **Why it matters for release**: generics are in nearly every non-trivial Java file. On the JDK
  `java.base` corpus (443 files reached) this was the largest single `InternalError` class. `Cli.finalizeFormatOutcome`
  (`javafmt-cli/src/main/java/io/javafmt/cli/Cli.java:226`) treats `InternalError` as an error, so a
  first-run `javafmt .` on a normal project prints a wall of "javafmt changed more than layout" and
  exits 1.
- **Suggested fix**: render `node.getTypeParameters()` via the existing
  `MethodRenderer.renderTypeParams` group and splice it between the simple name and the
  `extends`/`implements`/`permits` header in `ClassLikeRenderer.render`,
  `ClassLikeRenderer.buildTypeDeclHeader`'s callers, and `RecordRenderer.render` (before the
  component list). Add fixtures for a generic class, generic interface, generic record and a
  generic type with a bounded/intersection bound.
- **Effort**: M
- **Confidence**: confirmed

### [P0] Multi-variable `for` initializers repeat the type on every variable
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/LoopRenderer.java:25,33-40`;
  `javafmt-core/src/main/java/io/javafmt/builder/FieldRenderer.java:14`
- **What**: javac models `for (int i = 0, len = n; ...)` as two sibling `VariableTree`s.
  `joinInto` scans each one independently and `FieldRenderer.render` emits the full declared type
  for each, producing `for (int i = 0, int len = n; ...)` — not valid Java. Confirmed on
  `java.base/java/lang/CharSequence.java:284` of the JDK 25 sources; the verifier reports
  "output dropped nothing and added `int`" and freezes the file. `java/lang/AbstractStringBuilder.java`
  hits it five times.
- **Why it matters for release**: this is an ordinary loop idiom, not an exotic one, and the
  failure mode without verification is uncompilable output.
- **Suggested fix**: in `buildForHeader`, render the first initializer in full and subsequent
  initializers that share the same `VariableTree.getType()` identity as `name = init` only (the
  same shape `MultipleVariableDeclarations` already reasons about for statements).
- **Effort**: S
- **Confidence**: confirmed

### [P0] Adjacent unary operators are glued together: `- -x` becomes `--x`
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ExpressionRenderers.java:94-100`
- **What**: `renderUnary` concatenates the operator text directly onto the operand doc with no
  separator, so nested `UNARY_MINUS`/`UNARY_PLUS` collapse into `--` / `++`. Reproduced:
  `int a = - -x;` (x a parameter) formats to `final int a = --x;`, which both changes meaning and
  fails to compile because the parameter was made `final` by `FinalParameters`.
- **Why it matters for release**: this is the one confirmed case where javafmt emits wrong code
  *and the round-trip verifier does not catch it*. `TokenScanner` deliberately emits multi-char
  operators one mark at a time (`javafmt-core/src/main/java/io/javafmt/parser/TokenScanner.java:19-21,74`)
  and `RoundTripVerifier.compare` compares sorted multisets
  (`.../parser/RoundTripVerifier.java:76`), so `-`,`-`,`x` matches `-`,`-`,`x`. Any future
  adjacency bug in the same class is equally invisible.
- **Suggested fix**: in `renderUnary`, emit a space when the operand's rendering starts with the
  same sign character (or, more simply, when the child is itself a prefix `+`/`-`/`++`/`--`).
  Separately, consider having `TokenScanner` emit maximal-munch operators so the verifier can see
  adjacency changes at all.
- **Effort**: S
- **Confidence**: confirmed

### [P0] The documented enum trailing comma always trips the round-trip verifier
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/EnumRenderer.java:83-89` (the
  `IfBreak(",", "")` default branch); `javafmt-core/src/main/java/io/javafmt/parser/RoundTripVerifier.java:41`
- **What**: for a body-less enum whose author wrote no trailing `,` or `;`, the renderer adds a
  comma when the constant list breaks — which is exactly what `docs/rules.md § Enums` promises.
  The verifier counts that as an added token and `Javafmt.render` (`.../Javafmt.java:258-261`)
  discards the whole result. Reproduced with a 12-constant enum:
  `InternalError[... output dropped nothing and added `,` (31 tokens in, 32 out)]`, source returned
  unchanged. Also reproduced on `java.base/java/lang/StackWalker.java` (`enum ExtendedOption`).
  A body-less enum that fits on one line is not affected, so the existing fixtures
  (`idempotent/enum-constants-only-*.java`, all one-liners or already comma-terminated) never see it.
- **Why it matters for release**: enums with more than a handful of constants are everywhere, and
  every one of them comes back unformatted with an internal-error diagnostic.
- **Suggested fix**: decide the contract. Either make the trailing comma a *lint* edit so it lands
  in the post-lint source the verifier compares against, or drop the rule from `docs/rules.md` and
  have `trailingSeparator` return `Doc.Text("")` in the default branch. Also note
  `constantsBlockBeforeBodyMembers` (line 69) never emits a trailing comma when the enum has body
  members, so the documented rule is already only half-implemented.
- **Effort**: M
- **Confidence**: confirmed

### [P1] A comment that is the last thing in a class body is dropped
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/TypeBodyRenderer.java:19-30` (never
  calls `attacher.tail(node)`); compare `builder/MethodRenderer.java:113` and
  `builder/BlockRenderer.java:23`, which do
- **What**: `class L { void f() {...}\n    // @end\n}` loses `// @end`. The verifier catches it
  ("comments changed: output dropped `// @end`") and freezes the file. Hit twice in the JDK corpus
  (`java/lang/classfile/*/snippet-files/PackageSnippets.java`). `docs/rules.md § Javadoc and
  comments` explicitly promises "comments between the last statement and its closing brace".
- **Why it matters for release**: comment loss is the failure users trust a formatter least for,
  and the docs claim google-java-format parity on exactly this case.
- **Suggested fix**: thread the class body's tail comments through `TypeBodyRenderer.withBody`
  the way `MethodRenderer.appendBody` already does, and add an idempotent fixture for a
  class-body-tail comment (there is one for block tails, none for class bodies).
- **Effort**: S
- **Confidence**: confirmed

### [P1] A stray `;` member in a class body, or after a type declaration, is dropped
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ClassLikeRenderer.java:29` (member
  filter keeps only `VariableTree | MethodTree | ClassTree | BlockTree`)
- **What**: `class S { void f() {} ; int x; };` loses both semicolons; the verifier freezes the
  file. Nine JDK `java.base` files hit this (`java/io/File.java`, all the `CharacterData*.java`,
  `java/lang/Shutdown.java`, `java/lang/StackWalker.java`, …), typically from a `};` after a
  nested enum or class.
- **Why it matters for release**: harmless tokens, but they cost the user the whole file and emit
  an internal error, and they are common in older/generated code.
- **Suggested fix**: extend the `EmptyStatement` lint rule to strip class-body-level stray
  semicolons before the renderer runs, so the post-lint source the verifier compares against no
  longer has them.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Annotation-type element `default` values are dropped
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/MethodRenderer.java:106-116`
  (`appendBody` emits `;` when `getBody() == null` and never reads `MethodTree.getDefaultValue()`);
  `grep -rn getDefaultValue javafmt-core/src/main` → no hits
- **What**: `@interface Ann { String value() default "hello"; }` renders as `String value();`.
  Verifier: "output dropped `default`, `"hello"`, `default`, `3`, `default` and 3 more"; file
  frozen. Without verification the output changes a defaulted element into a required one.
- **Why it matters for release**: annotation declarations with defaults are routine, and the
  semantic change (optional → required element) breaks every user of the annotation.
- **Suggested fix**: render `" default " + scan(getDefaultValue())` before the `;` in
  `appendBody`. Extend `idempotent/annotation-type.java`, which currently has no defaults.
- **Effort**: S
- **Confidence**: confirmed

### [P1] `outer.new Inner()` loses its qualifier; `new <T>Foo()` loses its type arguments
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ExpressionRenderers.java:174-188`
- **What**: `renderNewClass` reads only `getIdentifier()`, `getArguments()` and `getClassBody()`.
  `NewClassTree.getEnclosingExpression()` and `NewClassTree.getTypeArguments()` are never read
  anywhere in the builder. Reproduced: `outer.new Inner()` → "output dropped `outer`, `.`";
  `new <String>B("x")` → "output dropped `String`, `<`, `>`". Both freeze the file.
- **Why it matters for release**: qualified inner-class creation is uncommon but not rare, and the
  un-verified output does not compile.
- **Suggested fix**: prefix the enclosing expression plus `.` when present, and emit the explicit
  constructor type arguments between `new` and the identifier.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Varargs with a type-use annotation degrade to an array parameter
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/MethodRenderer.java:92-99`
- **What**: the varargs branch requires `p.getType() instanceof ArrayTypeTree`, but for
  `static void f(String @N ... args)` javac hands back an `AnnotatedTypeTree` wrapping the array,
  so the fallback renders `String @N [] args`. Reproduced: "output dropped `.`, `.`, `.` and added
  `[`, `]`"; file frozen. Without verification the method's signature changes from varargs to an
  array parameter, breaking every call site that used varargs syntax.
- **Suggested fix**: unwrap `AnnotatedTypeTree` before the `ArrayTypeTree` test (and keep the
  annotations between the element type and the `...`).
- **Effort**: S
- **Confidence**: confirmed

### [P1] The 150-char line width is not enforced — several constructs have no wrap points at all
- **Where**:
  - array initializers: `builder/ExpressionRenderers.java:157` (`Doc.Text(", ")` separators, no
    `Doc.LINE`, no `Doc.Group`)
  - constructor-call arguments: `builder/ExpressionRenderers.java:176-181`
  - annotation arguments: `builder/ExpressionRenderers.java:250`, plus multi-line annotations
    collapsed onto one line at `builder/ModifierRenderer.java:73,83`
  - generic type arguments: `builder/TypeRenderers.java:88`
  - try-with-resources resource list: `builder/TryRenderer.java:34`
  - enum constant arguments: `builder/EnumRenderer.java:130`
  - lambda parameter list: `builder/LambdaRenderer.java:42`
  - deconstruction patterns: `builder/ExpressionRenderers.java:211`
- **What**: measured outputs: a 14-element `int[]` initializer → 162 chars; `new X(...)` with eight
  arguments → 171 chars; a `@SuppressWarnings({...})` with eleven strings → 156 chars. On the JDK
  corpus, 13 of roughly the first 200 files produced lines over 150, including a 711-char line in
  `java/lang/FdLibm.java` and 133 over-long lines in `java/lang/CharacterDataLatin1.java`.
- **Why it matters for release**: "150-character line width" is the headline rule in both
  `CLAUDE.md` and `docs/rules.md § Line width`. Shipping a formatter that leaves 700-char lines
  intact undercuts the contract.
- **Suggested fix**: give each of these the same `Group` + `SoftLine` + `intersperse(",", LINE)` +
  `continuationIndent` shape `MethodRenderer.buildParamsPart` and
  `MethodChainRenderer.buildCallSuffix` already use. Add a fixture per construct with an
  over-width input.
- **Effort**: M
- **Confidence**: confirmed

### [P1] No blank line between `package` and the first type when the file has no imports
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ImportSectionRenderer.java:17-19`
  (returns an empty stream when there are no imports); `builder/DocBuilder.java:170-171,180-187`
- **What**: every output for an import-less file is `package p;` immediately followed by
  `public class C {` on the next line. Not a compile problem, just wrong-looking, and it is the
  first thing a prospective user sees on a small file.
- **Why it matters for release**: `docs/rules.md § Blank lines` doesn't cover it, but no Java
  convention omits this blank line, and it is visible on literally every import-less file.
- **Suggested fix**: emit the separating `HARD_LINE` from `visitCompilationUnit` after the package
  declaration rather than from the import section. Add fixtures: only **2 of 78** idempotent
  fixtures declare a `package` at all (`file-header-comment.java`, `import-leading-comment.java`),
  and both have imports, which is why this was never caught.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Comments between `else` and `if` are hoisted above the whole `if` statement
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/IfRenderer.java:25-30` (emits the
  literal `"} else "` and never renders comments attached inside the else position)
- **What**: input
  `if (x > 0) { ... } else /* between */ if (y > 0) { ... }` formats to
  `/* between */` on its own line **above** `if (x > 0) {`. The comment is preserved but now reads
  as annotating the first branch. `docs/rules.md § Javadoc and comments` names this exact case:
  "A comment between `else` and `if` … they all land in the right place."
  Related relocations, all reproduced and all idempotent (so they stick):
  - a trailing `// note` on an argument or parameter becomes a *leading* comment on the next
    element (`builder/MethodRenderer.java:88`, `builder/MethodChainRenderer.java:105-107`), and
    the whole list is forced to break even when it fits;
  - a trailing comment on an opening `{` (`if (x) { // cond`) moves inside the block onto its own
    line above the first statement;
  - a trailing comment inside a method chain is hoisted out of the chain entirely **and** defeats
    the documented author-broke-chain preservation:
    `return s.strip() // step one` / `.toUpperCase();` becomes `// step one` above the `return`
    with the chain collapsed onto one line. `MethodChainRenderer.authorBrokeChain:58-65` asks
    `Recursor.isFollowedByNewline(ms.getExpression())`, and the next non-whitespace character
    after `s.strip()` is the `/` of the comment, not a newline. Without the comment the same input
    correctly stays multi-line — verified both ways.
- **Why it matters for release**: the comment chapter of `docs/rules.md` is written as a
  parity claim against google-java-format; the headline example in it is wrong today. Worse, the
  current behavior is **baked into the fixtures as expected output** —
  `test-fixtures/comment-else-if/expected.java`, `arg-list-interior-comment/expected.java`,
  `param-list-interior-comment/expected.java` and `comment-in-method-chain/expected.java` all
  encode the relocation, so the suite actively defends it.
- **Suggested fix**: give `IfRenderer` an else-position comment slot (the attacher already has
  `interior`/`tail` channels), keep a same-line comment attached to the element it trailed rather
  than promoting it to the next one, and make `isFollowedByNewline` skip over a trailing comment
  when looking for the author's line break. Then rewrite the four fixtures above — they are
  currently the reason nobody has noticed.
- **Effort**: L
- **Confidence**: confirmed

### [P1] A blank line is inserted between every class member, contradicting the documented rule
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/TypeBodyRenderer.java:27`
  (`Doc.intersperse(Doc.HARD_LINE, members...)` unconditionally)
- **What**: `private int a; private int b; public int c;` formats with a blank line between all
  three. `docs/rules.md § Blank lines` says "One blank line between field groups **when visibility
  changes**", which implies `a` and `b` stay adjacent.
- **Why it matters for release**: it is the most visible formatting decision javafmt makes on
  typical code, and the shipped docs describe something else. Either behavior is defensible; they
  just have to agree before the docs go public.
- **Suggested fix**: pick one. If the code is right, rewrite the `docs/rules.md` bullet to "one
  blank line between every member". If the docs are right, group consecutive same-visibility
  fields in `TypeBodyRenderer.withBody`. Add a fixture with three same-visibility fields either way.
- **Effort**: S (docs) / M (code)
- **Confidence**: confirmed

### [P1] `StackOverflowError` escapes `Javafmt.format`, which documents that it never throws
- **Where**: `javafmt-core/src/main/java/io/javafmt/Javafmt.java:265` catches
  `RuntimeException | AssertionError`; the recursion is `builder/DocBuilder.java:158-163` →
  `builder/ExpressionRenderers.java:57`
- **What**: a `1+1+1+…` expression with ~2,000 terms overflows the stack at the default size and
  the `StackOverflowError` propagates out of `Javafmt.formatWithResult`. The class javadoc
  (`Javafmt.java:27-30`) says "Formatting never throws for input that fails to parse or that
  javafmt itself chokes on". The CLI's `reportInternalError` only catches `RuntimeException`
  (`javafmt-cli/src/main/java/io/javafmt/cli/Main.java:13`).
- **Why it matters for release**: generated sources (long concatenations, giant literal tables)
  reach these depths, and the failure is a stack trace rather than "returned unchanged with a
  diagnostic".
- **Suggested fix**: catch `StackOverflowError` alongside `RuntimeException | AssertionError` in
  `Javafmt.render` / `internalErrorResult` and return the source unchanged with an `InternalError`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `module-info` target lists are reordered, which can change `ServiceLoader` order
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/ModuleRenderer.java:95-98`
- **What**: `renderTargeted` sorts the `to` list of `exports`/`opens` and the `with` list of
  `provides`. Sorting `provides S with ImplA, ImplB` changes declaration order, which HotSpot's
  `ServiceLoader` honours for module-declared providers — so `findFirst()` can pick a different
  implementation after formatting. `ModuleRenderer.bodyStream:42-45` also inserts blank lines
  between directive kinds. Neither behavior appears in `docs/rules.md`.
- **Suggested fix**: leave `provides … with` alone (the `to` list of `exports`/`opens` is a set and
  is safe to sort), and document both the sorting and the blank-line grouping in `docs/rules.md`.
- **Effort**: S
- **Confidence**: likely (the reorder is confirmed; the `ServiceLoader` consequence is
  implementation-defined, not spec-guaranteed)

### [P2] Colon-form `case X: { … }` moves the brace off the case line (not K&R)
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/SwitchExpressionRenderer.java:85-95`
  (the block statement goes through `Doc::indentedLine`)
- **What**: `case 3: { return 3; }` becomes `case 3:` / newline / indented `{` / body / `}`.
  `docs/rules.md § Braces` says K&R, opening brace on the same line.
- **Suggested fix**: special-case a sole `BlockTree` statement in `renderColonForm` and hug it to
  the `:` the way `renderCase`'s arrow form does.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `EnumRenderer`'s comment stripper is a regex that also matches inside string literals
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/EnumRenderer.java:105-107`
- **What**: `stripComments` runs `//[^\n]*` and `/\*.*?\*/` over the raw enum source to decide what
  the author's trailing separator was. A constant argument containing `//` or `/*`
  (`enum E { A("http://x"), B("y") }`) makes it strip real code and read the wrong last character.
  `docs/rules.md § Javadoc and comments` explicitly promises "the scanner only recognises `//` and
  `/*` outside string literals, character literals, and text blocks" — this path does not.
- **Suggested fix**: reuse `io.javafmt.parser.CommentScanner`/`TokenScanner`, which already do this
  correctly, instead of the regex.
- **Effort**: S
- **Confidence**: confirmed by code reading; a concrete mis-format was not produced (the contrived
  cases fell into the harmless default branch)

### [P2] Superlinear formatting cost on deeply nested chains and logical expressions
- **Where**: `javafmt-core/src/main/java/io/javafmt/doc/Doc.java:32` (`flatWidthOrBreak`) and
  `Doc.java:71` (`canBreakInternally`), called from `builder/MethodChainRenderer.java:43`,
  `builder/ExpressionRenderers.java:77,118`, `builder/SwitchExpressionRenderer.java:66`
- **What**: each of these walks the whole rendered subtree, and each nesting level calls it again
  on a subtree that contains all the deeper ones. Measured on nested `list.stream()…map(q -> <the
  next level>)…` chains: 902 chars → 7 ms, 1,682 → 12 ms, 3,242 → 55 ms, 4,802 → 164 ms — roughly
  quadratic to cubic in file size for that shape. Realistic nesting depths (5–10) are fine.
- **Suggested fix**: memoize flat width per `Doc` node (an `IdentityHashMap` built once after the
  Doc tree is complete, or a width field computed bottom-up during construction).
- **Effort**: M
- **Confidence**: confirmed (measurement); the exact exponent is not pinned down

### [P2] `Doc.Fill`, `GreedyPrintStrategy` and `Printer(int)` are not used in production
- **Where**: `javafmt-core/src/main/java/io/javafmt/doc/Doc.java:206-232` (`Fill`),
  `printer/PrintEngine.java:89-148` (`expandFill`, `endsMidLine`),
  `printer/GreedyPrintStrategy.java`, `printer/Printer.java:15-17`
- **What**: no builder constructs a `Doc.Fill` (`grep -rn "Doc.Fill" main/ | grep -v printer/ | grep
  -v doc/Doc.java` → nothing). `Javafmt` always passes `PrintStrategy.wadlerLindig()`, so
  `GreedyPrintStrategy` only runs in tests — yet `new Printer(lineWidth)` defaults to *greedy*, so a
  public-API caller who uses the one-arg constructor gets different output from `Javafmt.format`.
  All of `io.javafmt.doc` and `io.javafmt.printer` is `public`, so this ships as a supported surface.
- **Suggested fix**: either delete `Fill` and `GreedyPrintStrategy` or, at minimum, make
  `Printer(int)` default to the strategy production uses and say in the package javadoc that these
  packages are internal.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `MethodRenderer.render` returns `null` for `<init>` but `DocBuilder.dispatch` rejects null
- **Where**: `javafmt-core/src/main/java/io/javafmt/builder/MethodRenderer.java:20-23` vs
  `builder/DocBuilder.java:299-301` (`Objects.requireNonNull(result, …)`)
- **What**: a constructor reached through `Recursor.scan`/`scanNullable` would throw an NPE rather
  than be skipped. In practice `BodyMemberRenderer.render` intercepts `<init>` first and
  `ClassBodyRenderer` only sees anonymous-class / enum-constant bodies (which cannot declare
  constructors), so the path looks unreachable — but the `@Nullable` return advertises a contract
  the dispatcher does not honour.
- **Suggested fix**: drop the `@Nullable` early return and let `BodyMemberRenderer` remain the only
  route, or make `dispatch` propagate null.
- **Effort**: S
- **Confidence**: likely (unreachable today; not reproduced)

### [P2] Documentation disagrees with itself and with the code
- **Where**: `CLAUDE.md`, `docs/rules.md`, `ROADMAP.md`
- **What**:
  - `CLAUDE.md` § Formatting rules: "**Enums**: alphabetically sorted constants" — `docs/rules.md
    § Enums` says the opposite ("Constants kept in declaration order") and explains why, and the
    code agrees with `docs/rules.md` (`EnumRenderer` never sorts). `ROADMAP.md` open question 1
    ("Enum sorting… detect or always sort?") is settled but still listed as open.
  - `ROADMAP.md` Phase 1 item 6 lists "Member ordering" as delivered; `docs/rules.md § Member
    ordering` says javafmt performs no member reordering except the utility-class constructor,
    which is what `UtilityClassConstructor` does.
  - `CLAUDE.md` "Method chains: break before `.` if chain doesn't fit" omits the actual rule
    (80-char threshold **and** at least three links — `MethodChainRenderer.java:27,29`).
  - `docs/rules.md` § Enums promises a trailing comma that the code only emits in the one shape the
    verifier rejects (see the P0 above).
  - `RedundantTypeArguments` (`lint/RedundantTypeArguments.java`, 339 lines, rewrites
    `new Comparator<String>(){…}` to `new Comparator<>(){…}` — observed) appears in neither
    `docs/rules.md § Lint pass` nor `CLAUDE.md`'s rule list. *(lint is the other reviewer's scope —
    flagged here only because it changed output in my runs.)*
- **Suggested fix**: make `docs/rules.md` the single source of truth, then reduce `CLAUDE.md`'s
  formatting-rules section to a pointer plus the handful of rules that genuinely differ, and clear
  the settled `ROADMAP.md` open questions.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Fixture-suite gaps
- **Where**: `javafmt-core/src/test/resources/test-fixtures/` (70 input/expected pairs,
  78 idempotent files)
- **What**: the harness itself is good — `JavafmtFixtureTest` re-formats, checks idempotency,
  re-parses and differentially compiles before/after. The gaps are in what it covers. Nothing in
  the suite exercises:
  - any generic type declaration (`grep` for `class|interface|record <Name><` → zero hits) — which
    is why the P0 above survived;
  - a `package` declaration without imports (2 of 78 idempotent files have a `package`, both with
    imports);
  - annotation elements with `default` values (`idempotent/annotation-type.java` has none);
  - multi-variable `for` initializers;
  - a class-body tail comment (there is one for block tails);
  - an array initializer, constructor-call argument list, annotation argument list, type-argument
    list or try-with-resources header that exceeds the line width;
  - `outer.new Inner()`, `new <T>Foo()`, type-annotated varargs, `- -x`;
  - an enum that must wrap and has no author trailing comma (it cannot pass today).
- **Suggested fix**: add one fixture per confirmed defect above as part of fixing it; that alone
  closes most of the list.
- **Effort**: M
- **Confidence**: confirmed

## Cross-boundary notes (one line each, for the other reviewer)

- `RoundTripVerifier` is the reason most of the P0s above are freezes rather than corruption, but
  its multiset comparison plus `TokenScanner`'s one-char-per-operator lexing makes it blind to
  token *reordering* and to operator adjacency (`parser/TokenScanner.java:19-21`,
  `parser/RoundTripVerifier.java:76`).
- `lint/RedundantModifier` hangs (or is pathologically slow) on
  `java.base/java/lang/invoke/BoundMethodHandle.java` — a ~900-line file. Two independent runs
  both sat there for 5+ minutes of solid CPU; thread dumps show
  `RedundantModifier.removeModifier` (`lint/RedundantModifier.java:109` and `:120`) ←
  `checkClassMemberModifiers:63` ← `LintScan.edits:24` ← `LintEngine.applyRound:105`. Reproduce
  with `Javafmt.format(Files.readString(...BoundMethodHandle.java))`. This alone would make
  `javafmt .` appear to hang on a real codebase.
- `lint/RedundantTypeArguments` rewrites explicit type arguments on *anonymous* class creation
  (`new Comparator<String>(){}` → `new Comparator<>(){}`); that is legal since Java 9 only when the
  inferred type is denotable, and the rule is undocumented.
- `Cli.finalizeFormatOutcome` (`javafmt-cli/.../Cli.java:226`) turns every `InternalError` into
  exit code 1, so each of the freezes above is also a CI failure, not just a skipped file.

## Coverage notes

**Read fully**: all of `builder/` (29 files), all of `doc/` (3), all of `printer/` (7),
`parser/RoundTripVerifier.java`, `parser/TokenScanner.java` (partial), `Javafmt.java`,
`JavafmtConfig.java`, `ARCHITECTURE.md`, `docs/rules.md`, `ROADMAP.md`, `CLAUDE.md`,
`JavafmtFixtureTest.java`, the fixture directory listing and a sample of fixture contents.

**Exercised empirically**: compiled `javafmt-core/build/classes/java/main` against a JDK 25 runtime
and ran ~60 handwritten inputs covering records, sealed hierarchies, enums (five shapes), switch
(arrow, colon, patterns, guards, `null, default`), text blocks, modules, compact source files,
try-with-resources, anonymous and local classes, double-brace init, labels, varargs, type-use
annotations, unnamed variables, generics, C-style arrays, comments in eight positions, and
line-width overflow for nine constructs; plus a corpus run over JDK 25 `java.base` sources
(`java/util`, `java/lang`, `java/io`, `java/time`, `java/nio` — 1,276 files), which produced the
51-`InternalError` / 13-over-150 figures over the first 443 files before the run stalled inside
`RedundantModifier`. Every finding above was reproduced at least once.

**Skimmed**: `builder/*Test.java` (sizes and names only, not assertions),
`printer/PrinterTest.java`, `doc/DocTest.java`, `CommentAuditTest.java`, `DogfoodTest.java` —
I checked what they cover by name, not line by line.

**Did not get to**: `parser/CommentAttacher.java` (417 lines — the root cause of the comment
*relocation* findings almost certainly lives there, and it is the other reviewer's scope);
`lint/`, `cache/`, and the public API classes at the package root, other than where they explained
a builder-visible behavior; the JMH benchmark suite; the corpus run over the remaining ~1,000 JDK
files (it stalled in the lint pass — see the cross-boundary note).
