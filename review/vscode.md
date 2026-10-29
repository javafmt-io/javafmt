# javafmt-vscode review

## Summary

Not ready for a public Marketplace release. The formatting path works and the Java daemon side is
well-tested, but there is **no publishing pipeline at all** — no `vsce publish`, no Open VSX, no
`VSCE_PAT`/`OVSX_TOKEN` secret, and no VSIX anywhere in `release.yml` or `native.yml` — while
`docs/intro.md:36` already tells users to "Search **javafmt** in the Marketplace". The VSIX would
also be a bare listing: no README, no CHANGELOG, no LICENSE file, no icon, no keywords, and a
hardcoded `"version": "0.1.0"` that contradicts `PUBLISHING.md`'s "There is no version string in
any file to edit."

The runtime has two hang bugs that will bite on day one: the spawned daemon has **no `error`
listener**, so a failed spawn (missing `java`, non-executable binary) never rejects anything and
every format request hangs forever; and there is **no timeout on any request**, so the daemon's
own "drop the line and log" error path (`Daemon.java:26-28`) leaves the promise pending forever.
Combined with `formatOnSave`, that is a save that silently does nothing with a status bar stuck on
"ready".

Top 3: (1) no VS Code publishing pipeline; (2) unhandled spawn `error` → permanent hang;
(3) no request timeout + daemon silently drops failing requests.

Counts: 4 P0, 9 P1, 11 P2.

## Findings

### [P0] No VS Code Marketplace / Open VSX publishing pipeline exists

- **Where**: `.github/workflows/release.yml:1-56`, `.github/workflows/native.yml:99-142`,
  `PUBLISHING.md:110-118`, `docs/intro.md:34-36`
- **What**: `grep -rn "vsce\|ovsx\|VSCE_PAT\|OPEN_VSX" .github/` returns exactly one hit — a comment
  in `native.yml:99`. `release.yml` runs `gradle build` and `jreleaserFullRelease` (Maven Central +
  GitHub release) and never touches the VSIX. `native.yml` builds `javafmt-daemon` binaries for
  three targets and uploads them as **workflow artifacts only**; nothing stages them into
  `javafmt-vscode/bin/<target>/` and nothing invokes the `packageVsixTarget` task that
  `javafmt-vscode/build.gradle.kts:151-156` defines for exactly that purpose. `PUBLISHING.md`'s
  "What gets published" table has no VS Code row at all, yet `README.md:8` advertises it as "the
  Maven Central / Marketplace release process" and `docs/intro.md:36` tells users the extension is
  already searchable.
- **Why it matters for release**: The extension cannot be released. The published docs promise a
  Marketplace listing that will not exist.
- **Suggested fix**: Add a release job that `needs: native`, downloads the three
  `javafmt-daemon-<target>` artifacts into `javafmt-vscode/bin/<target>/`, runs
  `gradle :javafmt-vscode:packageVsixTarget` per target with `VSCE_TARGET` set, then
  `vsce publish --packagePath ...` and `ovsx publish ...` using `VSCE_PAT` / `OVSX_TOKEN` secrets;
  publish the universal (jar-fallback) VSIX last as the catch-all. Document it in `PUBLISHING.md`.
- **Effort**: M
- **Confidence**: confirmed

### [P0] Spawned daemon has no `error` listener — a spawn failure hangs every format request forever

- **Where**: `javafmt-vscode/src/extension.ts:164-195` (only `'exit'` is subscribed; `grep` for
  `on('error'` / `on('close'` across `src/**/*.ts` returns nothing)
- **What**: `child_process.spawn` reports async failures (ENOENT when `java` is not on PATH and the
  jar fallback is chosen, EACCES when the unpacked native binary lost its executable bit) by
  emitting `'error'`, not by exiting. Node does **not** guarantee an `'exit'` event after a spawn
  `'error'`; for ENOENT it emits `'error'` and `'close'` only. So `extension.ts:183` never runs, the
  `pending` map is never drained, `setStatus('error', ...)` never fires, and every
  `provideDocumentFormattingEdits` promise stays pending forever. The status bar still reads
  `$(check) javafmt` because `startDaemon` optimistically calls `setStatus('ready')` at line 167
  before the process has proved it can run.
- **Why it matters for release**: This is the single most likely first-run failure (no JVM
  installed, or a targeted VSIX whose binary did not survive extraction) and it presents as "Format
  Document does nothing, forever" with no error message.
- **Suggested fix**: Add `proc.on('error', err => { if (daemon === proc) { daemon = null; } reject
  all pending; setStatus('error', err.message); showErrorMessage(...) })` and move
  `setStatus('ready')` to after the first successful response rather than immediately after
  `spawn`. Add a unit test driving `mockProcess().emit('error', new Error('ENOENT'))`.
- **Effort**: S
- **Confidence**: confirmed

### [P0] No timeout on any request, and the daemon silently drops requests it cannot handle

- **Where**: `javafmt-vscode/src/extension.ts:252-258` (no `setTimeout` anywhere in `src/`);
  `javafmt-vscode/src/main/java/io/javafmt/vscode/Daemon.java:21-30`
- **What**: `sendRequest` registers a `pending` entry and writes the line; nothing ever times it
  out. On the daemon side, `serve` wraps each line in `catch (final RuntimeException e) { log.warn(
  "dropped malformed request line", e); }` — a dropped request produces **no response line at all**,
  so the matching promise never settles. Any JSON that deserializes to a `FormatRequest` with a null
  `source` (`Javafmt.formatWithResult` calls `Objects.requireNonNull`), or any unexpected runtime
  failure inside the formatter, silently hangs the editor request. `StackOverflowError` from deeply
  nested input is an `Error`, not a `RuntimeException`, so it escapes the `forEach` and kills the
  stream loop entirely.
- **Why it matters for release**: A hung format under `editor.formatOnSave` is a save that appears
  to do nothing; under `javafmt.formatOnSave` (`extension.ts:63`) VS Code's `waitUntil` budget
  expires and the save proceeds unformatted with only an internal log line. The `pending` map also
  leaks an entry per hung request for the lifetime of the session.
- **Suggested fix**: Wrap `sendRequest` in a `Promise.race` with a configurable timeout (5 s is
  generous for a warm daemon) that deletes the `pending` entry and rejects with a clear message; on
  the daemon side, replace the swallow in `Daemon.serve` with an error response line carrying the
  request id where one could be recovered, and catch `Throwable` so an `Error` does not end the
  loop.
- **Effort**: M
- **Confidence**: confirmed

### [P0] `package.json` version is hardcoded and disconnected from the tag-driven release

- **Where**: `javafmt-vscode/package.json:5` (`"version": "0.1.0"`),
  `build.gradle.kts:8-16` (axion-release derives the version from `v*`
  tags), `PUBLISHING.md:104` ("The version is derived from git tags... There
  is no version string in any file to edit.")
- **What**: Every other artifact takes its version from the git tag via axion-release; the VSIX
  takes it from a literal in `package.json` that nothing updates. `packageVsix`
  (`buildSrc/src/main/kotlin/javafmt.npm-conventions.gradle.kts:43-50`) runs plain `vsce package`
  with no `--no-git-tag-version`/version argument.
- **Why it matters for release**: The Marketplace rejects a republish of an already-published
  version. Tagging `v0.2.0` would either publish a VSIX labelled `0.1.0` or fail the second time
  around, and the extension version would never match the daemon version it bundles.
- **Suggested fix**: Have `packageVsix`/`packageVsixTarget` pass the Gradle project version to
  `vsce package --no-git-tag-version <version>` (or rewrite `package.json`'s version into a staging
  copy before packaging), and drop the literal from the committed manifest's meaning by documenting
  it as a placeholder.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No README, CHANGELOG, LICENSE file or icon ships in the VSIX

- **Where**: `javafmt-vscode/` (directory listing contains no `README.md`, `CHANGELOG.md`,
  `LICENSE`, or image file); `javafmt-vscode/.vscodeignore:5` negates `LICENSE`, which does not
  exist; `javafmt-vscode/package.json` has no `icon` and no `keywords`;
  `unzip -l javafmt-vscode/build/javafmt-vscode.vsix` lists 10 files, none of them documentation.
- **What**: The Marketplace listing would render "No README available", an empty Changelog tab, an
  empty License tab, and the default grey placeholder icon. `keywords` is absent, so the extension
  is only findable by its exact name — which is what `docs/intro.md:36` instructs users to do, but
  it puts the listing at a disadvantage against `redhat.java` and friends.
- **Why it matters for release**: This is the first thing every prospective user sees, and it is the
  single cheapest thing to get right.
- **Suggested fix**: Add `javafmt-vscode/README.md` (install, what it enforces, the `javafmt.*`
  settings, the daemon/jar story), `CHANGELOG.md`, a copy of the repo `LICENSE`, and a 128×128 PNG
  referenced by `"icon"`; add `"keywords": ["java", "formatter", "format on save", "lint",
  "checkstyle"]`.
- **Effort**: M
- **Confidence**: confirmed

### [P1] Whole-document replacement on every format destroys cursor position and folding

- **Where**: `javafmt-vscode/src/extension.ts:141-146`
- **What**: The provider returns a single `TextEdit.replace` spanning `(0,0)` to the end of the last
  line. The only mitigation is the `response.output === source` early return at line 141, which
  only helps when the file is already perfectly formatted. Any real reformat collapses all folded
  regions, resets the cursor and scroll position, and produces a whole-file diff in the editor's
  inline-diff views.
- **Why it matters for release**: Format-on-save is the headline use case; losing your fold state
  and cursor on every save is the most common complaint filed against formatter extensions.
- **Suggested fix**: Diff `source` against `response.output` (a simple common-prefix/common-suffix
  trim handles the overwhelming majority of cases; a line-level Myers diff handles the rest) and
  emit minimal `TextEdit`s instead of one full-range replace.
- **Effort**: M
- **Confidence**: confirmed

### [P1] CRLF documents are silently converted to LF and always produce a full rewrite

- **Where**: `javafmt-vscode/src/extension.ts:129,141-146`; no CRLF handling in `javafmt-core`
  (`grep -rn '\\r' javafmt-core/src/main/java/io/javafmt/printer/` returns nothing)
- **What**: `doc.getText()` returns text with the document's own EOL, so on a Windows CRLF file the
  request carries `\r\n` and the daemon returns LF-only output. The `output === source` guard at
  line 141 can therefore never be true on a CRLF file, so every format is a full-document
  replacement, and the replacement text converts the whole file to LF.
- **Why it matters for release**: Windows users on `core.autocrlf`/CRLF files get a whole-file
  line-ending change on first save — a diff of the entire file attributed to javafmt.
- **Suggested fix**: Detect `doc.eol === vscode.EndOfLine.CRLF`, normalise to `\n` before sending,
  and convert the daemon's output back to `\r\n` before comparing and building the edit.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Daemon stderr is discarded and there is no output channel — failures are undiagnosable

- **Where**: `javafmt-vscode/src/extension.ts:180-181`
  (`proc.stderr!.on('data', (_chunk: Buffer) => {});`)
- **What**: The daemon logs via slf4j-simple to stderr (correctly kept off the NDJSON stdout
  stream), and the extension throws all of it away. There is no `vscode.window.createOutputChannel`
  anywhere in `src/`. When the daemon drops a request (`Daemon.java:27` `log.warn`), the one piece
  of evidence that would explain it is deleted.
- **Why it matters for release**: Day-one bug reports will be "it stopped working" with nothing
  attached, and there is no way to ask a user for logs.
- **Suggested fix**: Create a `javafmt` output channel in `activate`, append daemon stderr lines to
  it, add a `javafmt.showOutput` command, and make the error-state status bar tooltip point at it.
- **Effort**: S
- **Confidence**: confirmed

### [P1] Targeted VSIXes ship no jar fallback, and only 3 of 5 declared targets are built

- **Where**: `javafmt-vscode/build.gradle.kts:141-149` (`stageTargetBinary` deletes
  `bin/javafmt-daemon.jar`), `javafmt-vscode/build.gradle.kts:113` (`vsceTargets` lists five),
  `.github/workflows/native.yml:30-37` (matrix builds `linux-x64`, `darwin-arm64`, `win32-x64`),
  `javafmt-vscode/src/extension.ts:240-244` (jar fallback branch)
- **What**: `stageTargetBinary` deliberately deletes the fat jar before packaging a per-platform
  VSIX, so in a shipped targeted build the `resolveDaemonCommand` jar-fallback branch is dead code:
  if the bundled native binary is missing or not executable, the user gets
  `"no native daemon for this platform and no daemon jar found — run \"gradle
  :javafmt-vscode:build\" to build it"` — a developer-facing message shown to an end user. Separately,
  `darwin-x64` (Intel Macs) and `linux-arm64` have no CI runner, so those users depend entirely on
  a universal VSIX being published alongside the targeted ones.
- **Why it matters for release**: Intel Mac and ARM Linux users either get nothing or get the
  developer-only error message; and a single bad extraction on any platform is unrecoverable.
- **Suggested fix**: Either keep the jar in targeted VSIXes as a genuine fallback (3 MB), or ensure
  the universal VSIX is published as the lowest-priority target; and rewrite the error message to
  tell an end user what to do (install a JDK, or file an issue) rather than naming a Gradle task.
- **Effort**: S
- **Confidence**: confirmed

### [P1] The request/response correlation logic is entirely untested

- **Where**: `javafmt-vscode/src/extension.ts:169-207,252-258`;
  `javafmt-vscode/src/test/unit/extension.test.ts` (covers `buildRequest`, `toDiagnostics`,
  `isJavaDocument`, `statusFor`, status-bar sync, spawn selection and the formatOnSave guard —
  nothing else)
- **What**: There is no test that a response line resolves the matching `pending` entry, that two
  concurrent requests are correlated to the right promises, that the `'exit'` handler rejects
  pending requests, that `stopDaemon` rejects them, or that `getFormattingEdits` returns `[]` when
  output equals input and a full-range edit otherwise. The `__mocks__/child_process.js` mock already
  provides EventEmitter streams, so these are cheap to write. `CLAUDE.md` makes TDD mandatory.
- **Why it matters for release**: The three P0 defects above all live in exactly the untested
  region, which is why they are still there.
- **Suggested fix**: Add unit tests that push NDJSON lines through the mocked `stdout` PassThrough
  and assert promise resolution, out-of-order correlation, exit-rejection, and edit construction.
- **Effort**: M
- **Confidence**: confirmed

### [P1] `javafmt.formatOnSave` duplicates work and can exceed VS Code's `onWillSave` budget

- **Where**: `javafmt-vscode/src/extension.ts:57-65`; `javafmt-vscode/package.json` (the
  `javafmt.formatOnSave` description: "Shorthand for setting editor.formatOnSave and
  [java].editor.defaultFormatter yourself")
- **What**: The implementation is an `onWillSaveTextDocument` listener, not a settings shorthand. If
  a user follows the description's own advice *and* enables the setting, the document is formatted
  twice per save (once through `waitUntil`, once through the formatter). VS Code caps
  `onWillSaveTextDocument` `waitUntil` participation at ~1.5 s; a first save that also has to cold
  start the jar-fallback daemon under a JVM will blow that budget and save unformatted, silently.
- **Why it matters for release**: The setting's stated contract and its behaviour differ, and the
  failure mode (first save on a cold daemon does nothing) looks exactly like the extension being
  broken.
- **Suggested fix**: Either drop the setting and document the two-line `settings.json` snippet in
  the README, or keep it but pre-warm the daemon at activation, guard against the double-format,
  and reword the description to say it participates in the save rather than that it sets other
  settings.
- **Effort**: S
- **Confidence**: confirmed (double-format and description mismatch); likely (budget overrun)

### [P1] `stdin.write` errors are unhandled — a dead daemon can throw into the extension host

- **Where**: `javafmt-vscode/src/extension.ts:256` (`daemon!.stdin!.write(...)`)
- **What**: Writing to a pipe whose peer has exited emits `'error'` (EPIPE) on the stdin stream.
  No `'error'` listener is attached to `proc.stdin` anywhere, so Node's EventEmitter rethrows it as
  an uncaught exception, and `write`'s callback-less form gives the caller nothing either. There is
  also no check that the write returned `true` (backpressure) before resolving — for a very large
  file the write is buffered, which is fine, but nothing observes a failed flush.
- **Why it matters for release**: An uncaught exception in the extension host during a crash-restart
  window, plus a `pending` entry that is never settled.
- **Suggested fix**: Attach `proc.stdin.on('error', ...)`, and pass a write callback that rejects the
  matching `pending` entry on failure.
- **Effort**: S
- **Confidence**: confirmed

### [P1] No crash backoff: a daemon that fails at startup is respawned on every format request

- **Where**: `javafmt-vscode/src/extension.ts:124-127,183-194`
- **What**: On exit the handler nulls `daemon`; the next `getFormattingEdits` immediately calls
  `startDaemon()` again. There is no attempt counter, no backoff, and no "give up and tell the user"
  path. A daemon that crashes on startup (bad binary, corrupt native image) will be respawned once
  per keystroke-triggered format for the rest of the session.
- **Why it matters for release**: Process-spawn storms are a visible CPU hog and produce a scroll of
  identical error toasts, since `getFormattingEdits` also calls `showErrorMessage` per failure.
- **Suggested fix**: Track consecutive spawn failures, stop after ~3, surface one sticky error with
  a "Restart daemon" action, and reset the counter on a successful response.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `.vscodeignore`'s `out/test/**` exclusion does not work — test code ships in the VSIX

- **Where**: `javafmt-vscode/.vscodeignore:1-6`; confirmed in
  `javafmt-vscode/build/javafmt-vscode.vsix` (contains `extension/out/test/suite/extension.test.js`
  and its `.map`); root cause at
  `javafmt-vscode/node_modules/@vscode/vsce/out/package.js:1278-1280`
- **What**: vsce's filter is `files.filter(f => !ignore.some(match) || negate.some(match))` — the
  negate list wins regardless of ordering, so `!out/**` on line 2 re-includes everything that
  `out/test/**` on line 6 excludes. Unlike `.gitignore`, a later pattern cannot re-exclude.
- **Why it matters for release**: Only ~3 KB of dead test code and source maps, but it is visible to
  anyone who unzips the VSIX and signals the ignore file was never verified.
- **Suggested fix**: Give the integration tests their own `tsconfig` with a separate `outDir` (e.g.
  `out-test/`) that `.vscodeignore`'s blanket `**` already excludes, or replace `!out/**` with
  explicit `!out/extension.js` / `!out/protocol.js` negations and drop the `.map` files too.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Protocol has no version handshake and no error response shape

- **Where**: `javafmt-vscode/src/protocol.ts:1-20`;
  `javafmt-vscode/src/main/java/io/javafmt/vscode/FormatResponse.java:7`
- **What**: `FormatRequest` is `{id, source}` and `FormatResponse` is `{id, output,
  diagnostics}` — no protocol version field on either side, and no discriminated error variant. A
  newer extension paired with an older bundled daemon (user-configured `javafmt.daemonJar`, or a
  stale binary after an upgrade) has no way to detect the mismatch. `parseResponse` casts blindly
  with `as FormatResponse` and validates nothing: a response missing `diagnostics` would throw
  inside `toDiagnostics` at `extension.ts:97`.
- **Why it matters for release**: Freezing the wire format now without a version field makes every
  future protocol change a breaking one.
- **Suggested fix**: Add a `version` field to the request and echo it (or reject) in the response,
  add an `error` variant, and have `parseResponse` validate the shape (`typeof id === 'string' &&
  typeof output === 'string' && Array.isArray(diagnostics)`) before returning.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Cancellation tokens are ignored; stale requests accumulate

- **Where**: `javafmt-vscode/src/extension.ts:42-54` (the `CancellationToken` parameter VS Code
  passes is not even declared)
- **What**: VS Code cancels formatting requests when the document changes underneath them. The
  extension ignores the token, so the daemon keeps working on stale text and the `pending` entry
  survives until a response arrives.
- **Why it matters for release**: Wasted work rather than a correctness bug, since a cancelled
  provider's edits are discarded by VS Code. Worth fixing once a timeout exists.
- **Suggested fix**: Accept the token, and on `onCancellationRequested` delete the `pending` entry
  and reject.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `javafmt.enable: false` still registers the providers and silently returns no edits

- **Where**: `javafmt-vscode/src/extension.ts:40-55,123`
- **What**: The setting is only checked inside `getFormattingEdits`, which returns `[]`. javafmt
  still appears in the "Configure Default Formatter" picker and, if selected, format-document
  appears to do nothing.
- **Why it matters for release**: A disabled formatter that still claims the language is a confusing
  state; the user cannot tell it apart from a broken one.
- **Suggested fix**: Register/dispose the providers from a `workspace.onDidChangeConfiguration`
  handler driven by `javafmt.enable`, or drop the setting in favour of the standard
  `editor.defaultFormatter` mechanism.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Configuration is read without a resource scope, so multi-root workspaces get the wrong values

- **Where**: `javafmt-vscode/src/extension.ts:62,123,150`
  (`vscode.workspace.getConfiguration('javafmt')` with no second argument)
- **What**: Without a resource URI, folder-scoped settings from the folder that actually owns the
  document are ignored; the first folder's (or the global) value wins.
- **Why it matters for release**: Low impact today — the settings are global-ish — but
  `javafmt.daemonJar` and `javafmt.javaExecutable` are exactly the kind a monorepo would set per
  folder.
- **Suggested fix**: Pass `doc.uri` as the scope in `getFormattingEdits`, and thread the active
  document's URI into `startDaemon`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Diagnostics are never cleared when a document is closed or when javafmt is disabled

- **Where**: `javafmt-vscode/src/extension.ts:140` (`diagnosticCollection.set(...)` is the only
  mutation; no `delete`/`clear` anywhere)
- **What**: Diagnostics set on a URI persist in the Problems panel after the file is closed, and
  after `javafmt.enable` is turned off.
- **Why it matters for release**: Stale entries in the Problems panel that the user cannot get rid
  of without reloading the window.
- **Suggested fix**: Subscribe to `workspace.onDidCloseTextDocument` and call
  `diagnosticCollection.delete(doc.uri)`; clear the whole collection when `javafmt.enable` flips to
  false.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `@types/vscode` resolves to 1.118.0 while `engines.vscode` claims `^1.85.0`

- **Where**: `javafmt-vscode/package.json:14-16,40` (`"engines": {"vscode": "^1.85.0"}`,
  `"@types/vscode": "^1.85.0"`); `javafmt-vscode/package-lock.json` resolves
  `node_modules/@types/vscode` to **1.118.0**
- **What**: vsce's compatibility check compares the *declared range* against `engines.vscode`, so
  packaging passes — but TypeScript compiles against the 1.118 typings. Any API added between 1.85
  and 1.118 would type-check cleanly and fail at runtime on the minimum supported VS Code. The
  current code only uses long-stable APIs, so this is latent rather than live.
- **Why it matters for release**: It is a trap for the next contributor, not a present defect.
- **Suggested fix**: Pin `"@types/vscode": "1.85.0"` (exact, no caret) so the compiler enforces the
  declared floor, or raise `engines.vscode` to the version you actually intend to support.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `stopDaemon` does not close stdin or dispose the readline interface

- **Where**: `javafmt-vscode/src/extension.ts:169,197-207`
- **What**: `readline.createInterface({ input: proc.stdout })` is created per daemon and never
  closed; `stopDaemon` calls `daemon.kill()` (SIGTERM) without first ending stdin. Each
  `javafmt.restartDaemon` leaks one readline interface and its listeners for the session.
- **Why it matters for release**: Minor. Note the *good* news: the daemon reads stdin to EOF
  (`Daemon.java:22` `in.lines()`), so if the extension host dies without calling `deactivate` the
  pipe closes and the daemon exits on its own — no orphaned processes.
- **Suggested fix**: Keep the `rl` handle alongside `daemon`, and in `stopDaemon` call `rl.close()`
  and `daemon.stdin?.end()` before `kill()`.
- **Effort**: S
- **Confidence**: confirmed

### [P2] Restarting the daemon rejects requests that were issued to the new daemon

- **Where**: `javafmt-vscode/src/extension.ts:183-194`
- **What**: The `'exit'` handler correctly guards the `daemon === proc` check for the active-process
  swap (line 185), but the `pending` drain below it is unconditional: the old process's delayed exit
  rejects and clears requests that were already sent to the *new* daemon.
- **Why it matters for release**: A narrow race, only reachable through the restart command, and the
  user just retries.
- **Suggested fix**: Move the pending drain inside the `daemon === proc` guard, or key `pending`
  entries by the process that owns them.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `npmTestIntegration`'s description claims a skip that does not exist

- **Where**: `buildSrc/src/main/kotlin/javafmt.npm-conventions.gradle.kts:32-39`
- **What**: The description says "skipped when 'code' is not on PATH", but the task has no `onlyIf`
  and `@vscode/test-cli` downloads its own VS Code build rather than using one from PATH. The task
  always runs.
- **Why it matters for release**: Inaccurate build documentation; it would send someone debugging a
  CI failure down the wrong path. (CI is fine — `build.yml:39-43` installs xvfb and runs
  `xvfb-run -a gradle build`, and `check` depends on `npmTestIntegration`, so the integration tests
  genuinely execute on Linux CI.)
- **Suggested fix**: Delete the second clause of the description, or add the `onlyIf` it describes.
- **Effort**: S
- **Confidence**: confirmed

### [P2] `extensionKind` is not declared; remote/WSL/container behaviour is unspecified

- **Where**: `javafmt-vscode/package.json` (no `extensionKind`),
  `javafmt-vscode/src/extension.ts:236` (binary resolved from `ctx.extensionPath` on the host that
  runs the extension)
- **What**: The extension spawns a platform-native binary, so it must run where a matching binary
  was installed. VS Code's default for a `main`-only extension is `["workspace", "ui"]`, which
  resolves to the remote side and therefore downloads the remote platform's targeted VSIX — the
  correct outcome — but nothing in the manifest states that intent, and nothing has been tested
  against a remote.
- **Why it matters for release**: If the default ever resolves to `ui` for this extension, a macOS
  client formatting a Linux remote workspace would spawn the macOS binary against remote files.
- **Suggested fix**: Declare `"extensionKind": ["workspace"]` and smoke-test one Remote-SSH or
  devcontainer session before publishing.
- **Effort**: S
- **Confidence**: likely (default resolution); unverified (actual remote behaviour)

### [P2] `bin/` is gitignored, so a clean checkout cannot package the extension without a full Gradle build

- **Where**: `.gitignore:12` (`bin/`), `javafmt-vscode/build.gradle.kts:129`
  (`copyDaemonJar` writes into `javafmt-vscode/bin/`)
- **What**: This is the right call (a 3 MB jar and a 48 MB native binary do not belong in git), but
  it means `npm run package` alone produces a broken VSIX with no daemon and no warning. Only
  `gradle :javafmt-vscode:build` works, which matches `CLAUDE.md`'s rule.
- **Why it matters for release**: A contributor running the documented-in-package.json `npm run
  package` script gets a silently broken artifact.
- **Suggested fix**: Have `packageVsix` fail fast if `bin/` contains neither a jar nor a target
  directory, and note in the module README that packaging goes through Gradle.
- **Effort**: S
- **Confidence**: confirmed

## Coverage notes

**Read fully**: `src/extension.ts`, `src/protocol.ts`, all three files under `src/__mocks__/`,
`src/test/unit/*.test.ts` (3 files), `src/test/suite/extension.test.ts`, all six Java sources under
`src/main/java/io/javafmt/vscode/`, all four Java tests, `package.json`, `tsconfig.json`,
`.vscodeignore`, `jest.config.js`, `.vscode-test.mjs`, `javafmt-vscode/build.gradle.kts`,
`buildSrc/src/main/kotlin/javafmt.npm-conventions.gradle.kts`, all five
`.github/workflows/*.yml`, root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
`jreleaser.yml`, `PUBLISHING.md`, `README.md`, `ROADMAP.md`, `ARCHITECTURE.md`, `.gitignore`.

**Verified by inspection of build output rather than source**: the packaged VSIX contents
(`unzip -l javafmt-vscode/build/javafmt-vscode.vsix`, 10 files, 2.7 MB), and vsce 3.9.1's actual
ignore-filter and README/LICENSE handling
(`node_modules/@vscode/vsce/out/package.js:1245-1280,534-535,677-733`).

**Skimmed**: `package-lock.json` — only to resolve `@types/vscode` (1.118.0), `@vscode/vsce`
(3.9.1) and `typescript` (5.9.3). There are no runtime `dependencies` at all, so nothing from
`node_modules` ships in the VSIX and there is no third-party supply-chain surface in the shipped
artifact. Nothing concerning found. `reachability-metadata.json` (4.2 KB) skimmed for coverage of
`FormatRequest`/`FormatResponse`/`DiagnosticDto` — all three are present with the accessors and
constructor Jackson needs, and `NativeDaemonRoundTripTest` plus `native.yml:127-133` gate it
properly. `javafmt-core/src/main/java/io/javafmt/Javafmt.java` read only far enough to confirm
`formatWithResult` never throws for unparseable input and that core writes nothing to stdout.

**Not covered**: I did not run any build, test, `vsce package` or `npm` command (per instructions),
so all behavioural claims come from reading code plus the already-built VSIX on disk. I did not
verify that the `javafmt` publisher ID is registered on the Marketplace or Open VSX, nor that the
`javafmt-io/javafmt` GitHub repo referenced in `package.json:9` is public. I did not test the
extension against a real remote (SSH/WSL/container) session, nor verify that the VSIX preserves the
native binary's executable bit through VS Code's extraction. `.vscode-test/` and `node_modules/`
were excluded per scope.
