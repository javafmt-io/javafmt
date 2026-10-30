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
    id("com.diffplug.spotless") version "8.10.4"
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

Search **javafmt** in the Marketplace. The extension bundles the native [`javafmt` binary](#cli) for your platform and runs it as one long-lived `javafmt --daemon` process. Set `javafmt.binaryPath` to use a different binary. No Java runtime is needed.

javafmt is VS Code's Java document formatter, so *Format Document* and VS Code's own format-on-save use it. To format on save, turn on `editor.formatOnSave` for Java in your `settings.json`:

```json
"[java]": {
    "editor.formatOnSave": true
}
```

If another Java extension also offers a formatter, add `"editor.defaultFormatter": "javafmt.javafmt"` to the same block.

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

See [Formatting rules](./rules.md).

## CLI reference

Run `javafmt --help` for every option and the exit codes.

## JSON diagnostics (`--diagnostics=json`)

With `--diagnostics=json`, stderr switches from human-readable text to NDJSON: one JSON object per line, one line per diagnostic.

```json
{"kind":"internal","message":"dropped token ';'","file":"Foo.java","line":3,"col":9}
```

- `kind` is one of `parse`, `lint`, `internal`, `warning`, `cli`. Only `internal` means javafmt itself is at fault — every other kind is a problem in your own source (`parse`, `lint`, `warning`) or a message from the CLI layer (`cli`), such as a file that couldn't be read.
- `file` is omitted when the input came from stdin.
- `line` and `col` are `0` when the position is unknown.
- When formatting from stdin fails, stdout still receives the original source unchanged — the error is reported on stderr, not by corrupting the piped output.
