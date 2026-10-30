import * as path from 'path';
import { DaemonEnvironment, resolveDaemonCommand, vsceTarget } from '../../extension';

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

describe('resolveDaemonCommand', () => {
    it('runs the configured binary in daemon mode, ahead of the bundled one and PATH', () => {
        expect(resolveDaemonCommand('/opt/javafmt/javafmt', environment(['/opt/javafmt/javafmt', bundled, onPath]))).toEqual({
            command: '/opt/javafmt/javafmt',
            args: ['--daemon'],
        });
    });

    it('throws a clear error naming the configured binary when it is not an executable file', () => {
        expect(() => resolveDaemonCommand('/custom/javafmt', environment([bundled, onPath]))).toThrow(/custom\/javafmt/);
    });

    it('uses the binary bundled for this platform when none is configured', () => {
        expect(resolveDaemonCommand('', environment([bundled, onPath]))).toEqual({ command: bundled, args: ['--daemon'] });
    });

    it('uses the bundled javafmt.exe on Windows', () => {
        const exe = path.join('/ext', 'bin', 'win32-x64', 'javafmt.exe');
        expect(resolveDaemonCommand('', environment([exe], { platform: 'win32' })).command).toBe(exe);
    });

    it('falls back to the first javafmt on PATH when nothing is bundled', () => {
        expect(resolveDaemonCommand('', environment([onPath, path.join('/opt/homebrew/bin', 'javafmt')]))).toEqual({
            command: onPath,
            args: ['--daemon'],
        });
    });

    it('looks for javafmt.exe on the Windows PATH', () => {
        const exe = path.join('/tools', 'javafmt.exe');
        expect(resolveDaemonCommand('', environment([exe], { platform: 'win32', searchPath: '/tools' })).command).toBe(exe);
    });

    it('throws a clear error when javafmt is neither configured, bundled nor on PATH', () => {
        expect(() => resolveDaemonCommand('', environment([]))).toThrow(/no javafmt binary found/);
    });

    it('treats a missing PATH as an empty one', () => {
        expect(() => resolveDaemonCommand('', environment(['javafmt'], { searchPath: undefined }))).toThrow(/no javafmt binary found/);
    });
});

describe('vsceTarget', () => {
    const kotlinVsceTargets = ['darwin-arm64', 'darwin-x64', 'linux-arm64', 'linux-x64', 'win32-x64'];

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

    it('returns an empty string for an unsupported platform', () => {
        expect(vsceTarget('freebsd', 'x64')).toBe('');
    });
});
