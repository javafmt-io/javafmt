---
title: javafmt
description: A fast, opinionated Java code formatter for Java 21+.
hide_table_of_contents: true
---

# javafmt

> A fast, opinionated Java code formatter for Java 21+.

```bash
echo "class X{}" | javafmt
```

```java
class X {}
```

## Why

The Java formatters that exist are either slow or always a language version behind. javafmt uses `javac`'s own parser (`com.sun.source.tree`), so when new syntax lands in the JDK it's already supported. The CLI ships as a fat JAR and as a platform-native binary via GraalVM `native-image`.

The style is opinionated on purpose. Every config knob is a debate your team would otherwise have to have. Pick javafmt if you'd rather skip that meeting.

## Get started

- [Install and configure](/docs/intro) — Maven, Gradle (Spotless), VS Code, IntelliJ IDEA, CLI
- [GitHub](https://github.com/javafmt-io/javafmt) — source, issues, releases
- [Maven Central](https://central.sonatype.com/namespace/io.javafmt) — published artifacts

## What it enforces

- 150-character lines, 4-space indent
- K&R braces (missing braces get auto-added)
- Member order: static fields -> instance fields -> constructors -> methods by visibility -> nested types
- Imports: static first, ASCII-sorted, one flat group
- Ruff-style lint pass: auto-fix what's safe, warn on what isn't

Full spec: [Formatting rules](/docs/rules).
