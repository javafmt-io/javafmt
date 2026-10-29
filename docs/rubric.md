---
sidebar_position: 3
title: Comparison
---

# Comparison

javafmt came out of reading [Why are there no decent code formatters for Java?](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/) by Jan Ouwens — and sharing his frustration. None of the existing formatters fit our needs either: we were spending too much time hand-fixing Checkstyle violations, and every option came with trade-offs we didn't want to make.

| Criterion              | Best of the rest                            | javafmt                  | How                                                                                                                                |
|------------------------|---------------------------------------------|--------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| **Maven integration**  | Decent (via Spotless or fmt-maven-plugin)   | Native                   | A real `javafmt-maven-plugin` with `check` and `format` goals. Drop it into `pom.xml`. Nothing else to install.                    |
| **Speed**              | google-java-format CLI is fast              | Same league              | O(n) Wadler-Lindig printer. Fat JAR plus GraalVM native-image gives roughly 10 ms cold start. No Node.js, no Eclipse runtime.       |
| **Formatting quality** | google-java-format, Prettier Java           | Comparable               | Same Doc-algebra approach Prettier uses, so line breaking is consistent and predictable.                                            |
| **Ergonomics**         | google-java-format wins on this             | On par                   | Zero config. No XML to export from an IDE. No version drift — formatting is treated as a contract.                                  |
| **IntelliJ plugin**    | IntelliJ's built-in formatter               | Good enough              | `javafmt-intellij` calls the same core, so what the IDE shows is what CI checks.                                                   |
| **Configurability**    | IntelliJ and Eclipse expose hundreds of knobs | Deliberately minimal    | Opinionated by design. Configurability gets added one option at a time, only where the case is overwhelming.                       |

## How javafmt addresses each complaint

- **"Anything not enforced by CI is merely a suggestion."** javafmt ships both a Gradle integration (via Spotless) and a Maven plugin, so either build system can fail on unformatted files.
- **"Maven is too slow for format-on-save."** javafmt ships IntelliJ and VS Code plugins so format-on-save is trivial in the two dominant IDEs. The CLI handles every other workflow.
- **"Formatting isn't stable between versions."** Any change to formatting behavior is treated as a major version bump. javafmt will live in pre-1.0.0 for a while because of it.
- **"Requires a full Node.js runtime."** javafmt is pure Java. Integrations cover Gradle (via Spotless), Maven, IntelliJ, VS Code, and a standalone CLI — pick whichever fits your existing workflow.
- **"Requires the IDE to configure."** Less config, fewer formatting debates. javafmt keeps it minimal — effectively none today — to avoid the bikeshed.
- **"No proper standalone tool."** This is exactly why `javafmt-cli` ships from day one.
