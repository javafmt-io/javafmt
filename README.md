# javafmt

Opinionated, high-performance Java code formatter for modern LTS (Java 21+).

See [DESIGN.md](DESIGN.md) for architecture and formatting rules.

## Prerequisites

This project uses [SDKMAN!](https://sdkman.io/) to pin the JDK and Gradle.
`.sdkmanrc` declares both and no Gradle wrapper is committed.

```bash
# Install SDKMAN (one-time): https://sdkman.io/install
curl -s "https://get.sdkman.io" | bash

# From the repo root, install and activate the pinned versions:
sdk env install
sdk env
```

With `sdkman_auto_env=true` in `~/.sdkman/etc/config`, `sdk env` runs automatically on `cd`.

## Build

```bash
gradle build
```

## Modules

| Module               | Build  | Purpose                                         |
|----------------------|--------|-------------------------------------------------|
| `javafmt-core`         | Gradle | Parser + formatter engine (zero runtime deps)   |
| `javafmt-cli`          | Gradle | Standalone CLI (fat JAR + GraalVM native-image) |
| `javafmt-spotless`     | Gradle | Spotless `FormatterStep` integration            |
| `javafmt-intellij`     | Gradle | IntelliJ IDEA plugin (placeholder)              |
| `javafmt-maven-plugin` | Gradle | Maven plugin (`javafmt:format`, `javafmt:check`)    |

The Maven plugin JAR and `plugin.xml` descriptor are produced by Gradle via the `org.gradlex.maven-plugin-development` plugin — no separate `mvn` build required.
