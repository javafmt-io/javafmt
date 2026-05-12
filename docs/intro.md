---
sidebar_position: 1
title: Getting started
---

# Getting started

A fast, opinionated Java formatter for Java 21+.

## Install

### Maven

```xml
<plugin>
    <groupId>io.javafmt</groupId>
    <artifactId>javafmt-maven-plugin</artifactId>
    <version>0.1.0</version>
</plugin>
```

`mvn io.javafmt:javafmt-maven-plugin:format` rewrites in place. `…:check` exits non-zero when anything's out of shape — that's the CI gate.

### Gradle (via Spotless)

```kotlin
spotless {
    java {
        custom("javafmt") { input -> io.javafmt.spotless.JavafmtFormatterStep.apply(input) }
    }
}
```

### VS Code

Search **javafmt** in the Marketplace.

### IntelliJ IDEA

Search **javafmt** in *Settings -> Plugins -> Marketplace*.

### CLI

Grab a platform binary from [GitHub Releases](https://github.com/javafmt-io/javafmt/releases):

```bash
javafmt MyClass.java          # rewrites in place
javafmt --check MyClass.java  # non-zero exit if anything needs reformatting
echo "class X{}" | javafmt    # stdin -> stdout
```

## What it enforces

The short version: 150-character lines, 4-space indent, K&R braces (always), strict member ordering, and a ruff-style lint pass that auto-fixes the safe checks and warns on the rest.

The full contract is in [Formatting rules](./rules.md).

## Configuration

There's basically nothing to configure. The one knob is `reorderMembers`: off by default in IDE integrations (since shuffling members is a structural change, not whitespace) and on by default in the CLI and Maven plugin.
