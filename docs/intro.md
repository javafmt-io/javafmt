---
sidebar_position: 1
title: Getting started
---

# Getting started

A fast, opinionated Java formatter for Java 25+.

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
buildscript {
    dependencies {
        classpath("io.javafmt:javafmt-spotless:0.1.0")
    }
}

plugins {
    id("com.diffplug.spotless") version "8.4.0"
}

spotless {
    java {
        addStep(io.javafmt.spotless.JavafmtFormatterStep.create())
    }
}
```

Use `addStep(…create())`, not `custom("javafmt") { … }`. A `custom` step's up-to-date key is the integer you pass to `bumpThisNumberIfACustomStepChanges`, so upgrading javafmt would leave `spotlessCheck` passing on stale formatting. `create()` keys on the javafmt version, so an upgrade reformats.

`JavafmtFormatterStep.create(JavafmtConfig)` takes a configuration — line width, excludes — instead of the defaults.

`create(JavafmtConfig, String)` additionally passes a compile classpath, which turns on the lint rules that need resolved types (redundant type witnesses, star-import explosion). These are the rules javafmt applies to its own source:

```kotlin
spotless {
    java {
        addStep(
            io.javafmt.spotless.JavafmtFormatterStep.create(
                io.javafmt.JavafmtConfig.defaults(),
                sourceSets["main"].compileClasspath.asPath,
            ),
        )
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

The short version: 150-character lines, 4-space indent, K&R braces (always), and a ruff-style lint pass that auto-fixes the safe checks and warns on the rest.

The full contract is in [Formatting rules](./rules.md).

## Configuration

There's basically nothing to configure — javafmt is opinionated and non-configurable on principle. See [Formatting rules](./rules.md).

## CLI reference

`javafmt --help` prints this same summary.

| Flag | Description |
|------|-------------|
| `--check` | Do not write anything; exit 1 if any file would be reformatted. |
| `--diagnostics=<fmt>` | Diagnostic format: `text` (default) or `json` (NDJSON on stderr). |
| `--exclude <glob>` | Skip paths matching the glob. Repeatable. |
| `--no-default-excludes` | Search `build`, `target`, `out`, `generated`, `node_modules` and `.git` too. |
| `--threads <n>` | Files to format concurrently. Defaults to the CPU count. |
| `--cache <path>` | Skip files unchanged since the last run recorded in this file. |
| `--analyze` | Enable lint rules that need resolved types. Requires a classpath. |
| `--classpath <cp>` | Classpath used by `--analyze`. |
| `--classpath-file <path>` | Read the `--analyze` classpath from a file. |
| `-` | Read from stdin, as with no file arguments at all. |
| `--` | Treat every following argument as a path, not a flag. |
| `-h`, `--help` | Show this message. |
| `--version` | Show the javafmt version. |

A directory argument is searched recursively, skipping any directory below it named `build`,
`target`, `out`, `generated`, `node_modules` or `.git` — build output and downloaded dependencies
get rewritten by whatever produced them, so formatting them is churn. The names are only skipped
below the directory you named, so `javafmt build` still formats what is inside `build`, and
`--no-default-excludes` turns the skipping off everywhere.

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | nothing to do |
| 1 | a file would be reformatted, or could not be formatted |
| 2 | bad usage, or a file could not be read or written |

### JSON diagnostics (`--diagnostics=json`)

With `--diagnostics=json`, stderr switches from human-readable text to NDJSON: one JSON object per line, one line per diagnostic.

```json
{"kind":"internal","message":"dropped token ';'","file":"Foo.java","line":3,"col":9}
```

- `kind` is one of `parse`, `lint`, `internal`, `warning`, `cli`. Only `internal` means javafmt itself is at fault — every other kind is a problem in your own source (`parse`, `lint`, `warning`) or a message from the CLI layer (`cli`), such as a file that couldn't be read.
- `file` is omitted when the input came from stdin.
- `line` and `col` are `0` when the position is unknown.
- When formatting from stdin fails, stdout still receives the original source unchanged — the error is reported on stderr, not by corrupting the piped output.
