import * as fs from 'fs';
import * as path from 'path';
import { resolveDaemonCommand, vsceTarget } from '../../extension';

jest.mock('fs');
const existsSync = fs.existsSync as jest.MockedFunction<typeof fs.existsSync>;

describe('resolveDaemonCommand', () => {
    afterEach(() => jest.resetAllMocks());

    it('prefers the bundled native daemon for this platform', () => {
        existsSync.mockImplementation((p) => !String(p).endsWith('.jar'));
        const resolved = resolveDaemonCommand('/ext', 'java', '');
        expect(resolved.command).toContain('javafmt-daemon');
        expect(resolved.command).not.toContain('.jar');
        expect(resolved.args).toEqual([]);
    });

    it('falls back to the bundled jar when no native binary is present', () => {
        existsSync.mockImplementation((p) => String(p).endsWith('.jar'));
        const resolved = resolveDaemonCommand('/ext', 'java', '');
        expect(resolved.command).toBe('java');
        expect(resolved.args).toEqual(['-jar', path.join('/ext', 'bin', 'javafmt-daemon.jar')]);
    });

    it('honours an explicitly configured jar path, bypassing native detection entirely', () => {
        existsSync.mockImplementation((p) => String(p) === '/custom/d.jar');
        const resolved = resolveDaemonCommand('/ext', 'java', '/custom/d.jar');
        expect(resolved.args).toEqual(['-jar', '/custom/d.jar']);
    });

    it('throws a clear error naming the configured jar when it does not exist', () => {
        existsSync.mockReturnValue(false);
        expect(() => resolveDaemonCommand('/ext', 'java', '/custom/missing.jar')).toThrow(/custom\/missing\.jar/);
    });

    it('throws a clear error when neither a binary nor a jar exists', () => {
        existsSync.mockReturnValue(false);
        expect(() => resolveDaemonCommand('/ext', 'java', '')).toThrow(/no native daemon/);
    });
});

// Node's process.arch/process.platform and Java's os.name/os.arch use different vocabularies,
// so vsceTarget() here and vsceTarget() in build.gradle.kts are two independent
// implementations of the same mapping. If they ever disagree — e.g. one side emits
// "darwin-arm64" and the other looks for "darwin-aarch64" — the extension silently falls
// back to the jar and nobody notices, because the fallback still works. This test pins
// the TypeScript side's output to the literal five-name set the Kotlin side hard-codes
// (javafmt-vscode/build.gradle.kts's `vsceTargets`), so a divergence fails a test instead
// of failing silently at runtime.
describe('vsceTarget', () => {
    const kotlinVsceTargets = ['darwin-arm64', 'darwin-x64', 'linux-arm64', 'linux-x64', 'win32-x64'];

    // Exact expected output per pair, not just set membership — membership alone would let a
    // swapped branch (e.g. linux returning win32-x64 instead of linux-x64) pass while still
    // breaking production: the packaged VSIX for that platform only ships its own target
    // directory, so probing the wrong one finds nothing and falls back to the jar silently.
    it.each([
        ['darwin', 'arm64', 'darwin-arm64'],
        ['darwin', 'x64', 'darwin-x64'],
        ['darwin', 'ia32', 'darwin-x64'],
        ['linux', 'arm64', 'linux-arm64'],
        ['linux', 'x64', 'linux-x64'],
        ['linux', 'ia32', 'linux-x64'],
        ['win32', 'x64', 'win32-x64'],
        ['win32', 'arm64', 'win32-x64'],
    ])('maps platform=%s arch=%s to exactly %s', (platform, arch, expected) => {
        expect(vsceTarget(platform, arch)).toBe(expected);
        expect(kotlinVsceTargets).toContain(expected);
    });

    it('returns an empty string for an unsupported platform (falls through to the jar, not an error)', () => {
        expect(vsceTarget('freebsd', 'x64')).toBe('');
    });
});
