---
sidebar_position: 2
title: Why javafmt
---

# Why javafmt

Every file in a codebase should look as if one person wrote it. When it does, a diff shows only changes in meaning, and a reviewer has no whitespace or brace placement to argue about.

That is harder to keep up now that coding agents write a large share of the code. An agent picks up style from whatever it read last and from training data that mixes every Java style in use, so over many sessions its output drifts away from the style the project started with. Instructions in a prompt or a `CLAUDE.md` slow the drift; they do not stop it. javafmt runs on every save and every build and puts each change back into the shape the [formatting rules](./rules.md) describe, whoever or whatever wrote it. The rules are written precisely enough that an agent can read them and follow them, and the formatter catches whatever it gets wrong.

## Where it came from

javafmt came out of reading Jan Ouwens's [Why are there no decent code formatters for Java?](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/), which tests the usual candidates against four requirements (Maven integration, speed, good output, and ergonomics) and finds that none meets all four. It also makes the case for CI: "Anything that's not enforced by CI is merely a suggestion, and may as well not exist."

The usual way to enforce style in CI is Checkstyle, but Checkstyle only reports. Every violation becomes a task for a person, or an agent, to fix by hand, and the build stays red until someone does. We were spending too much time on those fixes. javafmt makes the same checks and fixes them itself: a rule is an auto-fix when the rewrite is safe, a warning when it is not, and left to Checkstyle when no fix is possible (see [Lint pass](./rules.md#lint-pass-safe-rewrites)).

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

## How javafmt answers each complaint

- "Anything not enforced by CI is merely a suggestion." The Maven plugin, the Gradle plugin and the Spotless step can each fail the build on unformatted files.
- "Maven is too slow for format-on-save." The IntelliJ and VS Code plugins format on save in the two most common IDEs, and the CLI covers every other workflow.
- "Formatting isn't stable between versions." Any change to formatting behavior is a major version bump, which is why javafmt will stay below 1.0.0 for a while.
- "Requires a full Node.js runtime." javafmt is pure Java, with integrations for Maven, Gradle, Spotless, IntelliJ, VS Code and a standalone CLI.
- "Requires the IDE to configure." There is nothing to configure, so there is nothing to argue about.
- "No proper standalone tool." javafmt ships one: `javafmt-cli`, as a fat JAR and as native binaries.
