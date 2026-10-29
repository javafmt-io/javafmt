# Publishing to Maven Central

`javafmt-core`, `javafmt-spotless`, and `javafmt-maven-plugin` are published to Maven Central under the `io.javafmt` namespace. Releases happen by pushing a version tag — JReleaser handles signing, staging, and the GitHub release from there.

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

`GITHUB_TOKEN` is provided automatically by Actions — no secret needed for it.

### 5. Optional — dry-run a publish locally

```bash
# Stage artifacts locally without publishing
gradle publishAllPublicationsToStagingRepository \
  -Pversion=0.1.0 \
  -PJRELEASER_GPG_SECRET_KEY="$(gpg --armor --export-secret-keys <KEY_ID>)" \
  -PJRELEASER_GPG_PASSPHRASE=<passphrase>

# Check the staging dir — signed JARs, POMs, and .asc files
ls build/staging-deploy/io/javafmt/
```

---

## Releasing a new version

The version is derived from git tags by the axion-release plugin. There is no version string in any file to edit.

1. **Push a tag** — the release workflow fires on `v*` tags:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **Watch the run** at `github.com/javafmt-io/javafmt/actions`:
   - axion reads the tag and sets the project version to `0.1.0`
   - Every module builds and tests
   - Artifacts are signed and staged to `build/staging-deploy/`
   - JReleaser deploys to Maven Central and creates a GitHub release with a generated changelog

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
| `javafmt-intellij`      | —                       | JetBrains Marketplace (separate; future)     |

Every Maven Central artifact ships with a sources JAR, a Javadoc JAR, and GPG signatures.

---

## Troubleshooting

- **Signing fails.** Confirm `JRELEASER_GPG_SECRET_KEY` is the full armored block including the header and footer lines, and that it matches `JRELEASER_GPG_PASSPHRASE`.
- **Namespace not verified.** DNS TXT records can take up to 24 h to propagate; `dig TXT verify.javafmt.io` is the quickest sanity check.
- **Central upload rejected.** Make sure every required POM field is present — name, description, url, license, developer, scm. The full failure reason is in `build/jreleaser/trace.log` inside the uploaded artifact.
- **`maven-plugin-development` publication name.** The plugin creates a publication whose exact name varies by version. If the signing step fails for that module, run `gradle :javafmt-maven-plugin:outgoingVariants` locally to inspect the available publications.
