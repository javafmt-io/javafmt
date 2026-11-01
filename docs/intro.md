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

`mvn io.javafmt:javafmt-maven-plugin:format` rewrites in place. `…:check` exits non-zero when any file needs formatting, which makes it the CI gate.

### Gradle

```kotlin
plugins {
    id("io.javafmt") version "0.1.0"
}
```

The plugin adds a format and a check task for every source set (`javafmtFormatMain`, `javafmtCheckTest`, and so on), plus `javafmtFormat` and `javafmtCheck`, which run them all. `check` depends on `javafmtCheck`, so `gradle build` fails on unformatted files. Unformatted files, javafmt errors and warnings are reported through Gradle's Problems API, so they show up in the build output, the problems report and IDEs that read it.

```kotlin
javafmt {
    excludes = listOf("src/main/java/gen/**", "**/Generated*.java")
    analyze = true
}
```

`excludes` takes Ant-style patterns, matched against each source file's path relative to the project directory. A source directory outside the project directory is matched the same way, so its paths start with `../`. `analyze` (off by default) passes the source set's compile classpath to javafmt, which turns on the lint rules that need resolved types. `maxHeapSize` (default `1g`) and `jvmArgs` configure the worker JVM.

The plugin needs Gradle 9.1 or newer, the first release that supports Java 25 toolchains. It loads in any Gradle daemon JVM, but javafmt itself runs in a worker process on a Java 25 toolchain. If Gradle can't find one, `javafmtCheck` and `javafmtFormat` fail and say so. Either install a JDK 25, or let Gradle download one by adding the Foojay resolver to the `plugins` block of `settings.gradle.kts`. The error message names the exact line to add, with the resolver version the plugin was tested against:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "<version from the error message>"
}
```

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

`JavafmtFormatterStep.create(JavafmtConfig)` takes a configuration instead of the defaults. To skip files, use Spotless's own `targetExclude("src/main/java/gen/**")` in Gradle or `<excludes>` in the Spotless Maven plugin; the step formats and lints every file Spotless hands it.

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

See [Formatting rules](./rules.md), and [Why javafmt](./why.md) for the reasons behind them.

## CLI reference

Run `javafmt --help` for every option and the exit codes.

## JSON diagnostics (`--diagnostics=json`)

With `--diagnostics=json`, stderr switches from human-readable text to NDJSON: one JSON object per line, one line per diagnostic.

```json
{"kind":"internal","message":"dropped token ';'","file":"Foo.java","line":3,"col":9}
```

- `kind` is one of `parse`, `lint`, `internal`, `warning`, `cli`. Only `internal` means javafmt itself is at fault. Every other kind is a problem in your own source (`parse`, `lint`, `warning`) or a message from the CLI layer (`cli`), such as a file that couldn't be read.
- `file` is omitted when the input came from stdin.
- `line` and `col` are `0` when the position is unknown.
- When formatting from stdin fails, stdout still receives the original source unchanged, and the error goes to stderr.
