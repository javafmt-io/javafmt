# Publishing

Pushing a `v*` tag runs `.github/workflows/release.yml`. It publishes `javafmt-core`, `javafmt-spotless`, and `javafmt-maven-plugin` to Maven Central under the `io.javafmt` namespace (JReleaser handles signing, staging, and the GitHub release), the Gradle plugin to the Gradle Plugin Portal, the IntelliJ plugin to the JetBrains Marketplace, and the VS Code extension to the Visual Studio Marketplace and Open VSX.

---

## One-time setup

### 1. Claim the namespace on Maven Central

The `io.javafmt` group ID requires proving ownership of the `javafmt.io` domain.

- [ ] Sign in (or create an account) at [central.sonatype.com](https://central.sonatype.com)
- [ ] **Namespaces -> Add Namespace** and enter `io.javafmt`
- [ ] Sonatype hands back a DNS TXT record to add at your registrar (e.g. `verify.javafmt.io TXT <token>`)
- [ ] Add the TXT record at your registrar, then click **Verify Namespace**
- [ ] Wait for propagation (usually minutes, up to 24 h) and confirm the namespace shows **Verified**

### 2. Generate a Maven Central user token

- [ ] In your Central account, **Account -> Generate User Token**
- [ ] Save the username and token — they go into GitHub secrets in step 4

### 3. Generate a GPG signing key

Maven Central requires every artifact to be signed with a GPG key published to a public keyserver.

```bash
# Generate a new key (RSA 4096, no expiry is fine for a project key)
gpg --full-generate-key

# Find the new key ID
gpg --list-secret-keys --keyid-format=long

# Publish the public key to the keyservers Central checks
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
gpg --keyserver keys.openpgp.org     --send-keys <YOUR_KEY_ID>

# Armored secret key (paste into the GitHub secret below)
gpg --armor --export-secret-keys <YOUR_KEY_ID>

# Armored public key (paste into the GitHub secret below)
gpg --armor --export <YOUR_KEY_ID>
```

### 4. Add GitHub repository secrets

**Settings -> Secrets and variables -> Actions -> New repository secret**, and add:

| Secret name                  | Value                                                         |
|------------------------------|---------------------------------------------------------------|
| `JRELEASER_GPG_SECRET_KEY`   | Armored GPG secret key — the whole `-----BEGIN PGP...` block  |
| `JRELEASER_GPG_PUBLIC_KEY`   | Armored GPG public key — the whole `-----BEGIN PGP...` block  |
| `JRELEASER_GPG_PASSPHRASE`   | Passphrase for the GPG key                                    |
| `MC_USERNAME`                | Maven Central user token username                             |
| `MC_TOKEN`                   | Maven Central user token password                             |
| `GRADLE_PUBLISH_KEY`         | Gradle Plugin Portal API key                                  |
| `GRADLE_PUBLISH_SECRET`      | Gradle Plugin Portal API secret                               |
| `JETBRAINS_CERTIFICATE_CHAIN`    | IntelliJ plugin signing certificate (step 6)              |
| `JETBRAINS_PRIVATE_KEY`          | Encrypted IntelliJ plugin signing key (step 6)            |
| `JETBRAINS_PRIVATE_KEY_PASSWORD` | Password of that key (step 6)                             |
| `JETBRAINS_PLUGIN_TOKEN`         | JetBrains Marketplace upload token (step 7)               |
| `OVSX_PAT`                       | Open VSX access token (step 9)                            |

`GITHUB_TOKEN` is provided automatically by Actions — no secret needed for it. The Visual Studio Marketplace needs no secret either: it trusts the release workflow directly (step 8).

### 5. Optional — dry-run a publish locally

```bash
# Stage artifacts locally without publishing
gradle publishAllPublicationsToStagingRepository \
  -Pversion=0.1.0 \
  -PJRELEASER_GPG_SECRET_KEY="$(gpg --armor --export-secret-keys <KEY_ID>)" \
  -PJRELEASER_GPG_PASSPHRASE=<passphrase>

# Check the staging dir — signed JARs, POMs, and .asc files
ls build/staging-deploy/io/javafmt/

# Load and validate jreleaser.yml the way the release workflow does. The release runs the
# JReleaser CLI, not a Gradle plugin; `sdk env install` installs the version pinned in .sdkmanrc.
# Run it from a regular clone: JReleaser cannot read the repository from a git worktree.
JRELEASER_PROJECT_VERSION=0.1.0 jreleaser config --output-directory build
```

### 6. Generate the IntelliJ plugin signing key

The release signs the IntelliJ plugin before uploading it. Generate an encrypted key and a self-signed certificate (the Marketplace does not require one from a certificate authority); `openssl` asks for the key's password:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -aes-256-cbc -out private_encrypted.pem
openssl req -new -x509 -key private_encrypted.pem -days 3650 -subj "/CN=javafmt/O=javafmt.io" -out chain.crt

gh secret set JETBRAINS_CERTIFICATE_CHAIN    -R javafmt-io/javafmt < chain.crt
gh secret set JETBRAINS_PRIVATE_KEY          -R javafmt-io/javafmt < private_encrypted.pem
gh secret set JETBRAINS_PRIVATE_KEY_PASSWORD -R javafmt-io/javafmt
```

The last command prompts for the password. Keep the key and its password somewhere safe, then delete the local files.

### 7. Set up the JetBrains Marketplace

- [ ] Sign in at [plugins.jetbrains.com](https://plugins.jetbrains.com) with a JetBrains Account
- [ ] **Profile -> My Tokens -> Generate Token**, and save it as the `JETBRAINS_PLUGIN_TOKEN` secret
- [ ] The first version has to be uploaded by hand. On the first tagged release, the `publish-intellij-plugin` job signs the plugin, keeps the signed zip as the `intellij-plugin-signed` artifact of the run, and then fails to publish because the plugin does not exist yet. Download that artifact and upload the zip under **Profile -> Add new plugin**. JetBrains reviews a new plugin before listing it; every later release publishes automatically.

The plugin is listed as **javafmt (javafmt.io)**, because an unrelated plugin already uses the name "javafmt".

### 8. Set up the Visual Studio Marketplace

- [ ] Sign in at [marketplace.visualstudio.com/manage](https://marketplace.visualstudio.com/manage) and create a publisher with the ID `javafmt`, matching `publisher` in `javafmt-vscode/package.json`. The ID cannot be changed later.
- [ ] Open the publisher, select **Trusted Publishing -> Create**, and enter: repository owner `javafmt-io`, repository `javafmt`, workflow `release.yml`, no environment.

### 9. Set up Open VSX

Open VSX is the registry Cursor, Windsurf, VSCodium, and Gitpod install from.

- [ ] Sign in at [open-vsx.org](https://open-vsx.org) with GitHub, link an Eclipse account, and sign the Publisher Agreement
- [ ] **Settings -> Access Tokens**, generate a token, and save it as the `OVSX_PAT` secret
- [ ] Create the namespace, then ask for exclusive ownership of it by opening an issue at [EclipseFdn/open-vsx.org](https://github.com/EclipseFdn/open-vsx.org/issues):
  ```bash
  gradle :javafmt-vscode:npmInstall
  javafmt-vscode/node_modules/.bin/ovsx create-namespace javafmt -p <token>
  ```

Every release, the first included, publishes with `OVSX_PAT`. To stop keeping a token, register the workflow under **Settings -> Trusted Publishers** (namespace `javafmt`, GitHub Actions, `javafmt-io`, `javafmt`, `release.yml`) once a version exists, then delete the `OVSX_PAT` secret: without it, the release uses trusted publishing.

---

## Releasing a new version

The version is derived from the latest `v*` git tag by the `javafmt.versioning` settings plugin in `build-settings/`. There is no version string in any file to edit.

1. **Push a tag** — the release workflow fires on `v*` tags:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **Watch the run** at `github.com/javafmt-io/javafmt/actions`:
   - The build reads the tag and sets the project version to `0.1.0`
   - Every module builds and tests
   - Artifacts are signed and staged to `build/staging-deploy/`
   - The JReleaser CLI (version pinned in `.sdkmanrc`) deploys to Maven Central and creates a GitHub release with a generated changelog
   - Once the GitHub release exists, separate jobs publish the Gradle plugin, the signed IntelliJ plugin, and one VS Code extension per platform to the Visual Studio Marketplace and Open VSX

3. **Verify on Central** — new artifacts land at
   `https://central.sonatype.com/artifact/io.javafmt/javafmt-core` within a few minutes of deployment.

Between releases, `gradle currentVersion` shows something like `0.1.1-SNAPSHOT` — the next patch version, inferred from the last tag. No manual bumping.

---

## What gets published

| Module                  | Artifact ID             | Where it goes                                |
|-------------------------|-------------------------|----------------------------------------------|
| `javafmt-core`          | `javafmt-core`          | Maven Central                                |
| `javafmt-spotless`      | `javafmt-spotless`      | Maven Central                                |
| `javafmt-maven-plugin`  | `javafmt-maven-plugin`  | Maven Central                                |
| `javafmt-cli`           | —                       | GitHub Releases only (not on Central)        |
| `javafmt-gradle-plugin` | `io.javafmt` plugin     | Gradle Plugin Portal                         |
| `javafmt-intellij`      | —                       | JetBrains Marketplace and GitHub Releases    |
| `javafmt-vscode`        | —                       | Visual Studio Marketplace, Open VSX, and GitHub Releases |

Every Maven Central artifact ships with a sources JAR, a Javadoc JAR, and GPG signatures.

---

## Troubleshooting

- **Signing fails.** Confirm `JRELEASER_GPG_SECRET_KEY` is the full armored block including the header and footer lines, and that it matches `JRELEASER_GPG_PASSPHRASE`.
- **Namespace not verified.** DNS TXT records can take up to 24 h to propagate; `dig TXT verify.javafmt.io` is the quickest sanity check.
- **Central upload rejected.** Make sure every required POM field is present — name, description, url, license, developer, scm. The full failure reason is in `build/jreleaser/trace.log` inside the uploaded artifact.
- **`maven-plugin-development` publication name.** The plugin creates a publication whose exact name varies by version. If the signing step fails for that module, run `gradle :javafmt-maven-plugin:outgoingVariants` locally to inspect the available publications.
