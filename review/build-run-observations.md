# Observations from running `gradle :<module>:build` locally (2026-09-19)

- All unit/test tasks in the six modules passed. Failures were only:
- `spotlessJavaCheck` FAILED in every module: `UnsupportedClassVersionError ... class file version 69.0, this runtime only recognizes up to 65.0`.
  Cause: the Gradle daemon on this machine runs on JDK 21 (`java -version` = 21.0.11) while the dogfooded formatter step (`buildSrc/.../JavafmtFormatter.kt`, loaded in-daemon by Spotless) is compiled for Java 25 by the toolchain.
  Implication: the dogfooding check silently depends on the *daemon* JVM being >= 25, not the toolchain. Any contributor with a 21 daemon gets a red build. Needs `gradle/gradle-daemon-jvm.properties` (daemon toolchain) or a fail-fast check in `settings.gradle.kts` with a clear message, plus a README note.
- `:javafmt-vscode:npmTestIntegration` FAILED: `spawn .../.vscode-test/vscode-darwin-arm64-1.138.0/.../Electron ENOENT`. `.vscode-test/` only contains 1.136.1. The integration test downloads a VS Code build at test time (network dependent, un-pinned `version`), so it is flaky offline and drifts as VS Code releases.

## Verified by me 2026-09-19 (carried over from the previous prompt.md, still true)

- **`jreleaser.yml` is never read.** Root `build.gradle.kts` applies the JReleaser plugin but has no `jreleaser { configFile.set(file("jreleaser.yml")) }` block. `JRELEASER_GITHUB_TOKEN=dummy gradle jreleaserConfig --no-configuration-cache` writes `build/jreleaser/trace.log` whose resolved model has `signing.active: NEVER`, zero occurrences of `mavenCentral`, and none of the yml's `longDescription`/`overwrite`/changelog settings. So `jreleaserFullRelease` in `release.yml` would create a bare GitHub release and deploy nothing to Central. Also the previous note says JReleaser crashes under the configuration cache (`gradle.properties` enables it); `release.yml` does not pass `--no-configuration-cache`.
- Formatting spot-check with the built native CLI on a small file: no blank line after `package p;` (confirmed core-engine P1); a blank line IS inserted directly after `class A {` (violates the documented "no blanks after `{`" rule); all blank lines inside the method body are deleted (docs/rules.md says extras collapse to one); `if (x > 0) { return; }` is expanded to three lines although CLAUDE.md and rules.md promise the single-line form is allowed when it fits.
- `Automatic-Module-Name` is absent from every jar manifest (grep over buildSrc and module build files: no hits).
- `LICENSE` says "James Schneidereit"; `jreleaser.yml`, POMs and website say "Jim Schneidereit".
- `JavafmtConfig` has no `serialVersionUID` (previous note flagged it; grep confirms neither `serialVersionUID` nor `implements Serializable` appears, so check whether it is still Serializable via the Spotless step's State before acting).
- The old prompt.md's manual Central steps (namespace `io.javafmt`, DNS TXT on javafmt.io, Portal user token, GPG key `rsa4096/3264A646D762952F` expiring 2028-09-12, five repo secrets) are account work not visible in the repo; carry them forward verbatim.
