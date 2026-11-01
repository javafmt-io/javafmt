---
sidebar_position: 1
title: Getting started
---

# Getting started

A fast, opinionated Java formatter for Java 25+.

## Why javafmt

Every file in a codebase should look as if one person wrote it. When it does, a diff shows only changes in meaning, and a reviewer has no whitespace or brace placement to argue about.

That is harder to keep up now that coding agents write a large share of the code. An agent picks up style from whatever it read last and from training data that mixes every Java style in use, so over many sessions its output drifts away from the style the project started with. Instructions in a prompt or a `CLAUDE.md` slow the drift; they do not stop it. javafmt runs on every save and every build and puts each change back into the shape the [formatting rules](./rules.md) describe, whoever or whatever wrote it. The rules are written precisely enough that an agent can read them and follow them, and the formatter catches whatever it gets wrong.

javafmt came out of reading Jan Ouwens's [Why are there no decent code formatters for Java?](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/), which tests the usual candidates against four requirements (Maven integration, speed, good output, and ergonomics) and finds that none meets all four. It also makes the case for CI: "Anything that's not enforced by CI is merely a suggestion, and may as well not exist."

The usual way to enforce style in CI is Checkstyle, but Checkstyle only reports. Every violation becomes a task for a person, or an agent, to fix by hand, and the build stays red until someone does. We were spending too much time on those fixes. javafmt makes the same checks and fixes them itself: a rule is an auto-fix when the rewrite is safe, a warning when it is not, and left to Checkstyle when no fix is possible (see [Lint pass](./rules.md#lint-pass-safe-rewrites)).

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

`excludes` takes Ant-style patterns, matched against each source file's path relative to the project directory. A source directory outside the project directory is matched the same way, so its paths start with `../`. `analyze` (off by default) passes the source set's compile classpath to javafmt, which turns on the lint rules that need resolved types. `maxHeapSize` (default `1g`) and `jvmArgs` configure the worker JVM. `ratchetFrom` limits both tasks to the files changed since a git ref (see [Adopting javafmt gradually](#adopting-javafmt-gradually)).

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

Search **javafmt** in *Settings -> Plugins -> Marketplace*. It needs IntelliJ IDEA 2026.2 or later. Turn it on per project under *Settings -> Tools -> javafmt*.

The plugin formats inside the IDE by default. *Formatter (all projects): Native javafmt binary* uses the [`javafmt` binary](#cli) instead, for every project in the IDE: the path set there, else a `javafmt` on `PATH`, else the matching binary downloaded from the GitHub release. Each format's duration goes to `idea.log`, so the two can be compared.

### CLI

Grab a platform binary from [GitHub Releases](https://github.com/javafmt-io/javafmt/releases):

```bash
javafmt MyClass.java          # rewrites in place
javafmt --check MyClass.java  # non-zero exit if anything needs reformatting
echo "class X{}" | javafmt    # stdin -> stdout
```

## Adopting javafmt gradually

The first run on an existing codebase usually changes most of its files. To avoid one large reformatting commit, point javafmt at a git ref and it checks and formats only the files you have touched since your branch left that ref. Over time the whole codebase gets formatted, one change at a time.

A file counts as touched when it differs from the merge base of `HEAD` and the ref: committed on your branch, staged, changed in the working tree, or new and not ignored by git. Files changed on the ref after your branch left it do not count, and neither do deleted files. Whole files are formatted, not just the changed lines. javafmt runs the `git` executable, so git must be on the `PATH`. An unknown ref, or a project outside a git repository, fails the run.

CLI:

```bash
javafmt --ratchet-from=origin/main src/
javafmt --check --ratchet-from=origin/main src/
```

`--ratchet-from` filters the files found under the paths you pass. It cannot be used with stdin.

Gradle:

```kotlin
javafmt {
    ratchetFrom = "origin/main"
}
```

It applies to `javafmtCheck` and `javafmtFormat`. A check with `ratchetFrom` set runs every time and never comes from the build cache, because its result depends on the state of the git repository as well as on the source files.

Maven:

```xml
<plugin>
    <groupId>io.javafmt</groupId>
    <artifactId>javafmt-maven-plugin</artifactId>
    <version>0.1.0</version>
    <configuration>
        <ratchetFrom>origin/main</ratchetFrom>
    </configuration>
</plugin>
```

It applies to both goals, and `-Djavafmt.ratchetFrom=origin/main` sets it from the command line.

Spotless has its own `ratchetFrom`, which works the same way for the javafmt step:

```kotlin
spotless {
    ratchetFrom("origin/main")
    java {
        addStep(io.javafmt.spotless.JavafmtFormatterStep.create())
    }
}
```

In CI, fetch enough history for the merge base to exist: a shallow clone of one commit has no merge base with `origin/main`.

## What it enforces

See [Formatting rules](./rules.md).

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

## Comparison

| Criterion          | Best of the rest                              | javafmt          | How                                                                                                                      |
|--------------------|-----------------------------------------------|------------------|--------------------------------------------------------------------------------------------------------------------------|
| Maven integration  | Decent (via Spotless or fmt-maven-plugin)     | Native           | A real `javafmt-maven-plugin` with `check` and `format` goals. Add it to `pom.xml`; there is nothing else to install.    |
| Gradle integration | Spotless                                      | Native           | The `io.javafmt` plugin adds `javafmtCheck` to `check`. A Spotless step is available too.                                |
| Speed              | google-java-format CLI is fast                | Same league      | O(n) Wadler-Lindig printer. Fat JAR plus GraalVM native-image gives roughly 10 ms cold start. No Node.js or Eclipse.    |
| Formatting quality | google-java-format, Prettier Java             | Comparable       | The same Doc-algebra approach Prettier uses, so line breaking is consistent and predictable.                            |
| Ergonomics         | google-java-format wins on this               | On par           | Zero config and no XML to export from an IDE. Formatting output is a contract and does not drift between versions.      |
| IntelliJ plugin    | IntelliJ's built-in formatter                 | Good enough      | `javafmt-intellij` calls the same core, so what the IDE shows is what CI checks.                                        |
| Configurability    | IntelliJ and Eclipse expose hundreds of knobs | None             | Opinionated by design: there are no style options.                                                                       |

### How javafmt answers each complaint

- "Anything not enforced by CI is merely a suggestion." The Maven plugin, the Gradle plugin and the Spotless step can each fail the build on unformatted files.
- "Maven is too slow for format-on-save." The IntelliJ and VS Code plugins format on save in the two most common IDEs, and the CLI covers every other workflow.
- "Formatting isn't stable between versions." Any change to formatting behavior is a major version bump, which is why javafmt will stay below 1.0.0 for a while.
- "Requires a full Node.js runtime." javafmt is pure Java, with integrations for Maven, Gradle, Spotless, IntelliJ, VS Code and a standalone CLI.
- "Requires the IDE to configure." There is nothing to configure, so there is nothing to argue about.
- "No proper standalone tool." javafmt ships one: `javafmt-cli`, as a fat JAR and as native binaries.
