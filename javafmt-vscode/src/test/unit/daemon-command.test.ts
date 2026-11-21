import * as path from 'path';
import { DaemonEnvironment, resolveDaemonBinary, vsceTarget } from '../../extension';

function environment(executables: string[], overrides: Partial<DaemonEnvironment> = {}): DaemonEnvironment {
    return {
        extensionPath: '/ext',
        searchPath: ['/nope', '/usr/local/bin', '/opt/homebrew/bin'].join(path.delimiter),
        platform: 'linux',
        arch: 'x64',
        isExecutable: (p) => executables.includes(p),
        ...overrides,
    };
}

const bundled = path.join('/ext', 'bin', 'linux-x64', 'javafmt');
const onPath = path.join('/usr/local/bin', 'javafmt');

describe('resolveDaemonBinary', () => {
    it('picks the configured binary, ahead of the bundled one and PATH', () => {
        expect(resolveDaemonBinary('/opt/javafmt/javafmt', environment(['/opt/javafmt/javafmt', bundled, onPath]))).toEqual({
            binary: '/opt/javafmt/javafmt',
        });
    });

    it('names the configured binary when it is not an executable file', () => {
        expect(resolveDaemonBinary('/custom/javafmt', environment([bundled, onPath]))).toEqual({
            missing: expect.stringMatching(/custom\/javafmt/),
        });
    });

    it('uses the binary bundled for this platform when none is configured', () => {
        expect(resolveDaemonBinary('', environment([bundled, onPath]))).toEqual({ binary: bundled });
    });

    it('uses the bundled javafmt.exe on Windows', () => {
        const exe = path.join('/ext', 'bin', 'win32-x64', 'javafmt.exe');
        expect(resolveDaemonBinary('', environment([exe], { platform: 'win32' }))).toEqual({ binary: exe });
    });

    it('falls back to the first javafmt on PATH when nothing is bundled', () => {
        expect(resolveDaemonBinary('', environment([onPath, path.join('/opt/homebrew/bin', 'javafmt')]))).toEqual({
            binary: onPath,
        });
    });

    it('looks for javafmt.exe on the Windows PATH', () => {
        const exe = path.join('/tools', 'javafmt.exe');
        expect(resolveDaemonBinary('', environment([exe], { platform: 'win32', searchPath: '/tools' }))).toEqual({ binary: exe });
    });

    it('says so when javafmt is neither configured, bundled nor on PATH', () => {
        expect(resolveDaemonBinary('', environment([]))).toEqual({ missing: expect.stringMatching(/no javafmt binary found/) });
    });

    it('treats a missing PATH as an empty one', () => {
        expect(resolveDaemonBinary('', environment(['javafmt'], { searchPath: undefined }))).toEqual({
            missing: expect.stringMatching(/no javafmt binary found/),
        });
    });
});

describe('vsceTarget', () => {
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
    });

    it('returns an empty string for an unsupported platform', () => {
        expect(vsceTarget('freebsd', 'x64')).toBe('');
    });
});
