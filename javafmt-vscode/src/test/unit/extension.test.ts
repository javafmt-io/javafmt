import { create } from '@bufbuild/protobuf';
import { sizeDelimitedEncode } from '@bufbuild/protobuf/wire';
import { activate, deactivate, toDiagnostics, statusFor, vsceTarget } from '../../extension';
import { Diagnostic, DiagnosticSchema, FormatReplySchema, FormatRequest, Kind, Severity } from '../../gen/daemon_pb';
import * as child_process from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { MockProcess, mockProcess } from '../../__mocks__/child_process';
import { DiagnosticSeverity, LanguageStatusSeverity, Position, Range, mockOutputChannel, mockStatusItem } from '../../__mocks__/vscode';
import * as vscode from 'vscode';
import type * as vscodeTypes from 'vscode';
import { decodeRequest } from './wire';

function diagnostic(severity: Severity, kind: Kind, message: string, line: number, column: number): Diagnostic {
    return create(DiagnosticSchema, { severity, kind, message, line, column });
}

describe('toDiagnostics', () => {
    it('returns an empty array for no diagnostics', () => {
        expect(toDiagnostics([])).toEqual([]);
    });

    it.each([
        { line: 3, column: 5, expected: new Position(2, 4) },
        { line: 0, column: 0, expected: new Position(0, 0) },
    ])('puts a diagnostic at line $line, column $column on $expected', ({ line, column, expected }) => {
        const [d] = toDiagnostics([diagnostic(Severity.ERROR, Kind.PARSE, 'm', line, column)]);
        expect(d.range.start).toEqual(expected);
    });

    it.each([
        { severity: Severity.ERROR, expected: DiagnosticSeverity.Error },
        { severity: Severity.WARNING, expected: DiagnosticSeverity.Warning },
        { severity: 7 as Severity, expected: DiagnosticSeverity.Error },
    ])('maps severity $severity to $expected', ({ severity, expected }) => {
        const [d] = toDiagnostics([diagnostic(severity, Kind.LINT, 'm', 1, 1)]);
        expect(d.severity).toBe(expected);
    });

    it('sets range.start === range.end (zero-width point diagnostic)', () => {
        const [d] = toDiagnostics([diagnostic(Severity.WARNING, Kind.WARNING, 'w', 5, 10)]);
        expect(d.range.start).toEqual(d.range.end);
    });

    it('preserves the diagnostic message verbatim', () => {
        const [d] = toDiagnostics([diagnostic(Severity.WARNING, Kind.WARNING, 'EqualsHashCode', 1, 1)]);
        expect(d.message).toBe('EqualsHashCode');
    });

    it('labels the diagnostic as javafmt and carries its kind as the code', () => {
        const [d] = toDiagnostics([diagnostic(Severity.ERROR, Kind.INTERNAL, 'dropped token', 0, 0)]);
        expect(d.source).toBe('javafmt');
        expect(d.code).toBe('internal');
    });

    it('reports a problem in the .javafmt project file as an error with the config code', () => {
        const [d] = toDiagnostics([diagnostic(Severity.ERROR, Kind.CONFIG, '/repo/.javafmt: unknown setting "analyze.skp"', 0, 0)]);
        expect(d.severity).toBe(DiagnosticSeverity.Error);
        expect(d.code).toBe('config');
        expect(d.message).toContain('/repo/.javafmt');
    });
});

describe('statusFor', () => {
    it.each([
        { state: 'ready', detail: undefined, expected: { detail: 'ready', severity: LanguageStatusSeverity.Information, busy: false } },
        { state: 'formatting', detail: undefined, expected: { detail: 'formatting', severity: LanguageStatusSeverity.Information, busy: true } },
        { state: 'error', detail: 'daemon crashed', expected: { detail: 'daemon crashed', severity: LanguageStatusSeverity.Error, busy: false } },
        { state: 'error', detail: undefined, expected: { detail: 'error', severity: LanguageStatusSeverity.Error, busy: false } },
    ] as const)('$state with detail $detail', ({ state, detail, expected }) => {
        expect(statusFor(state, detail)).toEqual(expected);
    });
});

function makeContext(prefix = '/nonexistent-extension'): vscodeTypes.ExtensionContext {
    return {
        subscriptions: { push: jest.fn() },
        extensionPath: prefix,
        asAbsolutePath: (p: string) => `${prefix}${p}`,
    } as unknown as vscodeTypes.ExtensionContext;
}

function sentRequests(proc: MockProcess): FormatRequest[] {
    return (proc.stdin.write as jest.Mock).mock.calls.map((call) => decodeRequest(call[0] as Uint8Array));
}

function nextRequest(proc: MockProcess): Promise<FormatRequest> {
    return new Promise((resolve) => {
        proc.stdin.once('data', (bytes: Uint8Array) => resolve(decodeRequest(bytes)));
    });
}

function echoingProcess(): MockProcess {
    const proc = mockProcess();
    proc.stdin.on('data', (bytes: Uint8Array) => {
        const request = decodeRequest(bytes);
        reply(proc, request.id, request.source ?? '');
    });
    return proc;
}

function reply(proc: MockProcess, id: number, output: string): void {
    proc.stdout.write(sizeDelimitedEncode(FormatReplySchema, create(FormatReplySchema, { id, output })));
}

function onlyExecutable(isExecutable: (p: string) => boolean): void {
    (fs.accessSync as jest.Mock).mockImplementation((p: fs.PathLike) => {
        if (!isExecutable(String(p))) {
            throw new Error(`ENOENT ${String(p)}`);
        }
    });
    (fs.statSync as jest.Mock).mockImplementation((p: fs.PathLike) => ({ isFile: () => isExecutable(String(p)) }));
}

function realFileSystem(): void {
    const actual = jest.requireActual<typeof fs>('node:fs');
    (fs.accessSync as jest.Mock).mockImplementation(actual.accessSync);
    (fs.statSync as jest.Mock).mockImplementation(actual.statSync);
}

function configureBinaryPathOnce(binaryPath: string): void {
    (vscode.workspace.getConfiguration as jest.Mock).mockReturnValueOnce({
        get: jest.fn((key: string, defaultValue: unknown) => (key === 'binaryPath' ? binaryPath : defaultValue)),
    });
}

function configurationChanged(affects: (section: string) => boolean): void {
    const listener = (vscode.workspace.onDidChangeConfiguration as jest.Mock).mock.calls.at(-1)[0] as (e: { affectsConfiguration: (section: string) => boolean }) => void;
    listener({ affectsConfiguration: affects });
}

function diagnostics(): { set: jest.Mock; delete: jest.Mock } {
    return (vscode.languages.createDiagnosticCollection as jest.Mock).mock.results.at(-1)?.value;
}

function restartCommand(): () => void {
    return (vscode.commands.registerCommand as jest.Mock).mock.calls.find((call) => call[0] === 'javafmt.restartDaemon')[1];
}

function formatProvider(): vscodeTypes.DocumentFormattingEditProvider & {
    provideDocumentFormattingEdits: (doc: unknown) => Promise<vscodeTypes.TextEdit[]>;
} {
    return (vscode.languages.registerDocumentFormattingEditProvider as jest.Mock).mock.calls.at(-1)[1];
}

function document(uri: object = {}): unknown {
    return {
        getText: () => 'class A{}',
        uri,
        lineCount: 1,
        lineAt: () => ({ range: { end: new Position(0, 9) } }),
    };
}

function fileUri(fsPath: string): object {
    return { scheme: 'file', fsPath, toString: () => `file://${fsPath}` };
}

type ClasspathResult = { projectRoot: string; classpaths: string[]; modulepaths: string[] };

type JavaExtensionApi = {
    serverMode: string;
    getClasspaths: jest.Mock<Promise<ClasspathResult>, []>;
    isTestFile: jest.Mock<Promise<boolean>, []>;
    onDidClasspathUpdate: jest.Mock;
    onDidProjectsImport: jest.Mock;
    onDidProjectsDelete: jest.Mock;
};

const MAIN_SOURCE = '/repo/src/main/java/com/acme/Order.java';

const CLASSPATH = ['/repo/build/classes/java/main', '/cache/billing.jar'];

function event(): jest.Mock {
    return jest.fn(() => ({ dispose: jest.fn() }));
}

function javaExtension(overrides: Partial<JavaExtensionApi> = {}, isActive = true): JavaExtensionApi {
    const api: JavaExtensionApi = {
        serverMode: 'Standard',
        getClasspaths: jest.fn(async () => ({ projectRoot: 'file:///repo', classpaths: CLASSPATH, modulepaths: [] })),
        isTestFile: jest.fn(async () => false),
        onDidClasspathUpdate: event(),
        onDidProjectsImport: event(),
        onDidProjectsDelete: event(),
        ...overrides,
    };
    (vscode.extensions.getExtension as jest.Mock).mockImplementation((id: string) => (id === 'redhat.java' ? { isActive, exports: api } : undefined));
    return api;
}

describe('after activate()', () => {
    beforeEach(() => {
        onlyExecutable(() => false);
        deactivate();
        activate(makeContext());
    });

    describe('language status item', () => {
        it('is created for java documents and restarts the daemon when clicked', () => {
            expect(vscode.languages.createLanguageStatusItem).toHaveBeenCalledWith('javafmt', { language: 'java' });
            expect(mockStatusItem.name).toBe('javafmt');
            expect(mockStatusItem.text).toBe('javafmt');
            expect(mockStatusItem.command).toEqual({ title: 'Restart daemon', command: 'javafmt.restartDaemon' });
        });
    });

    describe('format on save', () => {
        it('is left to VS Code’s editor.formatOnSave rather than a save hook of its own', () => {
            expect(vscode.workspace.onWillSaveTextDocument).not.toHaveBeenCalled();
        });
    });

    describe('registerDocumentRangeFormattingEditProvider', () => {
        it('registers a range formatting provider for java', () => {
            expect(vscode.languages.registerDocumentRangeFormattingEditProvider)
                .toHaveBeenCalledWith('java', expect.any(Object));
        });
    });

    describe('diagnostics of a closed document', () => {
        it('are cleared when the document closes', () => {
            const uri = {};
            const listener = (vscode.workspace.onDidCloseTextDocument as jest.Mock).mock.calls.at(-1)[0] as (doc: { uri: object }) => void;
            listener({ uri });
            expect(diagnostics().delete).toHaveBeenCalledWith(uri);
        });
    });

    describe('the restart command', () => {
        it('says nothing was restarted, and why, when no binary can be started', () => {
            restartCommand()();
            expect(vscode.window.showInformationMessage).not.toHaveBeenCalled();
            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(expect.stringContaining('no javafmt binary found'));
        });
    });

    afterEach(() => {
        realFileSystem();
    });

    const bundled = path.join('/ext', 'bin', vsceTarget(process.platform, process.arch), process.platform === 'win32' ? 'javafmt.exe' : 'javafmt');

    const itOnPosix = process.platform === 'win32' ? it.skip : it;

    describe('startDaemon spawn behavior', () => {
        afterEach(() => {
            jest.restoreAllMocks();
            deactivate();
        });

        afterEach(() => {
            jest.useRealTimers();
        });

        it('spawns the binary bundled for this platform in daemon mode, with no Java runtime involved', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            onlyExecutable((p) => /javafmt(\.exe)?$/.test(p));
            deactivate();
            activate(makeContext('/ext'));
            expect(spawnSpy).toHaveBeenCalledWith(bundled, ['--daemon'], { stdio: ['pipe', 'pipe', 'ignore'] });
        });

        itOnPosix('restores the executable bit the VSIX zip dropped from the bundled binary before running it', () => {
            realFileSystem();
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            const extensionPath = fs.mkdtempSync(path.join(os.tmpdir(), 'javafmt-ext-'));
            const binary = path.join(extensionPath, 'bin', vsceTarget(process.platform, process.arch), 'javafmt');
            fs.mkdirSync(path.dirname(binary), { recursive: true });
            fs.writeFileSync(binary, '#!/bin/sh\n', { mode: 0o644 });
            try {
                deactivate();
                activate(makeContext(extensionPath));
                expect(fs.statSync(binary).mode & 0o777).toBe(0o744);
                expect(spawnSpy).toHaveBeenCalledWith(binary, ['--daemon'], expect.any(Object));
            } finally {
                fs.rmSync(extensionPath, { recursive: true, force: true });
            }
        });

        it('falls back to javafmt on PATH when nothing is bundled', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            onlyExecutable((p) => p !== bundled && /javafmt(\.exe)?$/.test(p));
            deactivate();
            activate(makeContext('/ext'));
            expect(spawnSpy).toHaveBeenCalledWith(expect.not.stringContaining(path.join('/ext', 'bin')), ['--daemon'], expect.any(Object));
        });

        it('spawns the binary configured in javafmt.binaryPath', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            onlyExecutable((p) => p === '/custom/javafmt' || p === bundled);
            configureBinaryPathOnce('/custom/javafmt');
            deactivate();
            activate(makeContext('/ext'));
            expect(spawnSpy).toHaveBeenCalledWith('/custom/javafmt', ['--daemon'], expect.any(Object));
        });

        it('reports a missing binary once per session, not on every format', async () => {
            await formatProvider().provideDocumentFormattingEdits(document());
            await formatProvider().provideDocumentFormattingEdits(document());
            expect(vscode.window.showErrorMessage).toHaveBeenCalledTimes(1);
            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(expect.stringContaining('no javafmt binary found'));
        });

        it('restarts the daemon when a javafmt setting changes', () => {
            const first = mockProcess();
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementationOnce(() => first).mockImplementation(() => mockProcess());
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            configurationChanged((section) => section === 'javafmt');
            expect(first.kill).toHaveBeenCalled();
            expect(spawnSpy).toHaveBeenCalledTimes(2);
        });

        it('leaves the daemon alone when an unrelated setting changes', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            configurationChanged((section) => section === 'editor');
            expect(spawnSpy).toHaveBeenCalledTimes(1);
        });

        it('treats a process that fails to start like one that exited, and starts a new one on the next format', async () => {
            const first = mockProcess();
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementationOnce(() => first).mockImplementation(() => mockProcess());
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            first.emit('error', new Error('spawn EACCES'));
            expect(mockStatusItem.detail).toContain('EACCES');
            expect(mockStatusItem.severity).toBe(LanguageStatusSeverity.Error);
            void formatProvider().provideDocumentFormattingEdits(document());
            expect(spawnSpy).toHaveBeenCalledTimes(2);
        });

        it('stops a daemon whose input pipe fails', () => {
            const first = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => first);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            first.stdin.emit('error', new Error('write EPIPE'));
            expect(first.kill).toHaveBeenCalled();
        });

        it('installs the output the daemon replies with', async () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const sent = nextRequest(proc);
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            reply(proc, (await sent).id, 'class A {}\n');
            await expect(edits).resolves.toEqual([expect.objectContaining({ newText: 'class A {}\n' })]);
        });

        it('stops a daemon that sends something other than a reply', async () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            proc.stdout.write(Buffer.from([3, 0xff, 0xff, 0xff]));
            await expect(edits).resolves.toEqual([]);
            expect(proc.kill).toHaveBeenCalled();
            expect(mockStatusItem.detail).toContain('not part of its protocol');
        });

        it('does not fail requests to a new daemon when the one it replaced exits', async () => {
            const first = mockProcess();
            const second = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementationOnce(() => first).mockImplementation(() => second);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            configurationChanged((section) => section === 'javafmt');
            const sent = nextRequest(second);
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            first.emit('exit', 1);
            first.emit('error', new Error('spawn EACCES'));
            reply(second, (await sent).id, 'class A {}\n');
            await expect(edits).resolves.toEqual([expect.objectContaining({ newText: 'class A {}\n' })]);
            expect(second.kill).not.toHaveBeenCalled();
        });

        it('formats the whole document for a range request', async () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const provider = (vscode.languages.registerDocumentRangeFormattingEditProvider as jest.Mock).mock.calls.at(-1)[1] as {
                provideDocumentRangeFormattingEdits: (doc: unknown, range: unknown) => Promise<vscodeTypes.TextEdit[]>;
            };
            const sent = nextRequest(proc);
            const edits = provider.provideDocumentRangeFormattingEdits(document(), new Range(new Position(0, 0), new Position(0, 1)));
            reply(proc, (await sent).id, 'class A {}\n');
            await expect(edits).resolves.toEqual([
                { range: new Range(new Position(0, 0), new Position(0, 9)), newText: 'class A {}\n' },
            ]);
        });

        it('says the daemon restarted when the restart command starts a new one', () => {
            jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            restartCommand()();
            expect(vscode.window.showInformationMessage).toHaveBeenCalledWith('javafmt daemon restarted.');
        });

        it('clears the diagnostics of a document whose formatting fails', async () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const uri = {};
            const sent = nextRequest(proc);
            const edits = formatProvider().provideDocumentFormattingEdits(document(uri));
            await sent;
            proc.emit('exit', 1, null);
            await expect(edits).resolves.toEqual([]);
            expect(diagnostics().delete).toHaveBeenCalledWith(uri);
        });

        it('shows a failure with one javafmt prefix', async () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const sent = nextRequest(proc);
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            await sent;
            proc.emit('exit', 1, null);
            await edits;
            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith('javafmt: daemon exited with code 1');
        });

        it('reports the signal that killed the daemon', () => {
            const proc = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            proc.emit('exit', null, 'SIGKILL');
            expect(mockStatusItem.detail).toBe('daemon exited with signal SIGKILL');
        });

        it('ignores replies from a daemon it replaced', async () => {
            const first = mockProcess();
            const second = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementationOnce(() => first).mockImplementation(() => second);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            configurationChanged((section) => section === 'javafmt');
            const sent = nextRequest(second);
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            const { id } = await sent;
            reply(first, id, 'class Stale {}\n');
            reply(second, id, 'class A {}\n');
            await expect(edits).resolves.toEqual([expect.objectContaining({ newText: 'class A {}\n' })]);
        });

        it('gives up on a request after 10s and restarts the daemon', async () => {
            jest.useFakeTimers();
            const first = mockProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => first);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
            const sent = nextRequest(first);
            const edits = formatProvider().provideDocumentFormattingEdits(document());
            await sent;
            await jest.advanceTimersByTimeAsync(10_000);
            await expect(edits).resolves.toEqual([]);
            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith('javafmt: did not respond within 10s');
            expect(first.kill).toHaveBeenCalled();
        });
    });

    describe('the file and its classpath', () => {
        let proc: MockProcess;

        beforeEach(() => {
            proc = echoingProcess();
            jest.spyOn(child_process, 'spawn').mockImplementation(() => proc);
            onlyExecutable((p) => p === bundled);
            deactivate();
            activate(makeContext('/ext'));
        });

        afterEach(() => {
            jest.restoreAllMocks();
            (vscode.extensions.getExtension as jest.Mock).mockReset();
            deactivate();
            jest.useRealTimers();
        });

        async function format(uri: object = fileUri(MAIN_SOURCE)): Promise<FormatRequest> {
            await formatProvider().provideDocumentFormattingEdits(document(uri));
            const request = sentRequests(proc).at(-1);
            if (!request) {
                throw new Error('no request was sent');
            }
            return request;
        }

        it('sends the path of a file on disk', async () => {
            expect((await format()).path).toBe(MAIN_SOURCE);
        });

        it('sends no path for a document that is not a file on disk', async () => {
            expect((await format({ scheme: 'untitled', fsPath: 'Untitled-1', toString: () => 'untitled:Untitled-1' })).path).toBeUndefined();
        });

        it('sends no classpath without the Java extension, and says so once in its output channel', async () => {
            expect((await format()).classpath).toBeUndefined();
            expect((await format()).classpath).toBeUndefined();
            expect(mockOutputChannel.info).toHaveBeenCalledTimes(1);
            expect(mockOutputChannel.info).toHaveBeenCalledWith(expect.stringContaining('redhat.java'));
        });

        it('sends the runtime classpath of a main source from the Java extension', async () => {
            const java = javaExtension();
            expect((await format()).classpath).toBe(CLASSPATH.join(path.delimiter));
            expect(java.getClasspaths).toHaveBeenCalledWith(`file://${MAIN_SOURCE}`, { scope: 'runtime' });
            expect(mockOutputChannel.info).not.toHaveBeenCalled();
        });

        it('sends the test classpath of a test source', async () => {
            const java = javaExtension({ isTestFile: jest.fn(async () => true) });
            await format(fileUri('/repo/src/test/java/com/acme/OrderTest.java'));
            expect(java.isTestFile).toHaveBeenCalledWith('file:///repo/src/test/java/com/acme/OrderTest.java');
            expect(java.getClasspaths).toHaveBeenCalledWith('file:///repo/src/test/java/com/acme/OrderTest.java', { scope: 'test' });
        });

        it('adds the module path of a modular project', async () => {
            javaExtension({ getClasspaths: jest.fn(async () => ({ projectRoot: 'file:///repo', classpaths: [], modulepaths: ['/cache/invoice.jar'] })) });
            expect((await format()).classpath).toBe('/cache/invoice.jar');
        });

        it('sends an empty classpath for a project without dependencies, which still turns the rules on', async () => {
            javaExtension({ getClasspaths: jest.fn(async () => ({ projectRoot: 'file:///repo', classpaths: [], modulepaths: [] })) });
            expect((await format()).classpath).toBe('');
        });

        it('asks the Java extension once for the files of one directory', async () => {
            const java = javaExtension();
            await format();
            expect((await format(fileUri('/repo/src/main/java/com/acme/Invoice.java'))).classpath).toBe(CLASSPATH.join(path.delimiter));
            expect(java.getClasspaths).toHaveBeenCalledTimes(1);
        });

        it.each(['onDidClasspathUpdate', 'onDidProjectsImport', 'onDidProjectsDelete'] as const)('asks again after the Java extension fires %s', async (name) => {
            const java = javaExtension();
            await format();
            const listener = java[name].mock.calls[0][0] as () => void;
            listener();
            await format();
            expect(java.getClasspaths).toHaveBeenCalledTimes(2);
        });

        it('sends no classpath when the Java extension does not answer within 2s', async () => {
            jest.useFakeTimers();
            javaExtension({ getClasspaths: jest.fn(() => new Promise<ClasspathResult>(() => {})) });
            void formatProvider().provideDocumentFormattingEdits(document(fileUri(MAIN_SOURCE)));
            await jest.advanceTimersByTimeAsync(1_999);
            expect(sentRequests(proc)).toEqual([]);
            await jest.advanceTimersByTimeAsync(1);
            expect(sentRequests(proc).map((request) => request.classpath)).toEqual([undefined]);
            expect(mockOutputChannel.info).toHaveBeenCalledWith(expect.stringContaining('2s'));
        });

        it('sends no classpath when the Java extension cannot give one, and says so once', async () => {
            javaExtension({ getClasspaths: jest.fn(async () => Promise.reject(new Error('Given URI does not belong to any Java project'))) });
            expect((await format()).classpath).toBeUndefined();
            expect((await format()).classpath).toBeUndefined();
            expect(mockOutputChannel.info).toHaveBeenCalledTimes(1);
            expect(mockOutputChannel.info).toHaveBeenCalledWith(expect.stringContaining('does not belong to any Java project'));
        });

        it('does not ask a Java language server that runs in lightweight mode', async () => {
            const java = javaExtension({ serverMode: 'LightWeight' });
            expect((await format()).classpath).toBeUndefined();
            expect(java.getClasspaths).not.toHaveBeenCalled();
        });

        it('does not use a Java extension that is installed but not active', async () => {
            const java = javaExtension({}, false);
            expect((await format()).classpath).toBeUndefined();
            expect(java.getClasspaths).not.toHaveBeenCalled();
        });
    });
});
