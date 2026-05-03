# Publishing to Maven Central

This project publishes `javafmt-core`, `javafmt-spotless`, and `javafmt-maven-plugin` to
Maven Central under the `io.javafmt` namespace. Releases are driven by pushing a version tag;
JReleaser handles signing, staging, and the GitHub release.

---

## One-time setup checklist

### 1. Verify namespace ownership on Maven Central

The group ID `io.javafmt` requires proving you own the `javafmt.io` domain.

- [ ] Sign in (or create an account) at [central.sonatype.com](https://central.sonatype.com)
- [ ] Go to **Namespaces → Add Namespace** and enter `io.javafmt`
- [ ] Sonatype will give you a DNS TXT record to add at your registrar (e.g., `verify.javafmt.io TXT <token>`)
- [ ] Add the TXT record at your registrar, then click **Verify Namespace**
- [ ] Wait for propagation (usually minutes, up to 24 h) and confirm the namespace is **Verified**

### 2. Generate a Maven Central user token

- [ ] In your Central account, go to **Account → Generate User Token**
- [ ] Save the username and token — you will need them for GitHub secrets (step 4)

### 3. Generate a GPG signing key

Maven Central requires all artifacts to be signed with a GPG key published to a public keyserver.

```bash
# Generate a new key (RSA 4096, no expiry is fine for a project key)
gpg --full-generate-key

# List keys to find your new key ID
gpg --list-secret-keys --keyid-format=long

# Publish the public key to the keyservers Maven Central checks
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
gpg --keyserver keys.openpgp.org     --send-keys <YOUR_KEY_ID>

# Export the armored secret key (paste into GitHub secret below)
gpg --armor --export-secret-keys <YOUR_KEY_ID>

# Export the armored public key (paste into GitHub secret below)
gpg --armor --export <YOUR_KEY_ID>
```

### 4. Add GitHub repository secrets

Go to **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret name                  | Value                                               |
|------------------------------|-----------------------------------------------------|
| `JRELEASER_GPG_SECRET_KEY`   | Armored GPG secret key (entire `-----BEGIN PGP...` block) |
| `JRELEASER_GPG_PUBLIC_KEY`   | Armored GPG public key (entire `-----BEGIN PGP...` block) |
| `JRELEASER_GPG_PASSPHRASE`   | Passphrase for the GPG key                          |
| `MC_USERNAME`                | Maven Central user token username                   |
| `MC_TOKEN`                   | Maven Central user token password                   |

`GITHUB_TOKEN` is provided automatically by Actions — no secret needed for it.

### 5. (Optional) Test a dry run locally

```bash
# Stage artifacts locally without publishing
gradle publishAllPublicationsToStagingRepository \
  -Pversion=0.1.0 \
  -PJRELEASER_GPG_SECRET_KEY="$(gpg --armor --export-secret-keys <KEY_ID>)" \
  -PJRELEASER_GPG_PASSPHRASE=<passphrase>

# Inspect build/staging-deploy/ — it should contain signed JARs, POMs, and .asc files
ls build/staging-deploy/io/javafmt/
```

---

## Releasing a new version

The version is derived automatically from git tags via the axion-release-plugin — there is no
version string to edit in any file.

1. **Push a tag** — the release workflow triggers on `v*` tags:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **Watch the Actions run** at `github.com/javafmt-io/javafmt/actions`:
   - axion reads the tag and sets the project version to `0.1.0`
   - Builds and tests all modules
   - Signs and stages artifacts to `build/staging-deploy/`
   - JReleaser deploys to Maven Central and creates a GitHub release with changelog

3. **Verify on Maven Central** — new publications appear at
   `https://central.sonatype.com/artifact/io.javafmt/javafmt-core` within a few minutes of
   the deployment completing.

Between releases, `gradle currentVersion` will show something like `0.1.1-SNAPSHOT` (the next
patch version, automatically inferred from the last tag). No manual bumping needed.

---

## What gets published

| Module                  | Artifact ID             | Published to        |
|-------------------------|-------------------------|---------------------|
| `javafmt-core`          | `javafmt-core`          | Maven Central       |
| `javafmt-spotless`      | `javafmt-spotless`      | Maven Central       |
| `javafmt-maven-plugin`  | `javafmt-maven-plugin`  | Maven Central       |
| `javafmt-cli`           | —                       | GitHub Releases only (not on Central) |
| `javafmt-intellij`      | —                       | JetBrains Marketplace (separate, future) |

Each Maven Central artifact ships with a sources JAR, a Javadoc JAR, and GPG signatures.

---

## Troubleshooting

- **Signing fails**: confirm `JRELEASER_GPG_SECRET_KEY` is the full armored block including
  the header/footer lines, and that it matches the `JRELEASER_GPG_PASSPHRASE`.
- **Namespace not verified**: DNS TXT records can take up to 24 h to propagate; use
  `dig TXT verify.javafmt.io` to check.
- **Central upload rejected**: ensure all required POM fields are present (name, description,
  url, license, developer, scm). Check `build/jreleaser/trace.log` in the uploaded artifact.
- **maven-plugin-development publication name**: the `maven-plugin-development` plugin creates
  a publication whose exact name may vary by version. If the signing step fails for that module,
  run `gradle :javafmt-maven-plugin:outgoingVariants` locally to inspect available publications.
