import * as vscode from 'vscode';
import * as child_process from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import type { Readable, Writable } from 'stream';
import { Diagnostic, FileScope, FormatReply, Kind, Severity, decodeReplies, encodeRequest } from './protocol';

type PendingRequest = {
    resolve: (reply: FormatReply) => void;
    reject: (err: Error) => void;
};

type JavaExtensionApi = {
    serverMode?: string;
    getClasspaths?: (uri: string, options: { scope: 'runtime' | 'test' }) => Promise<{ classpaths: string[]; modulepaths: string[] }>;
    isTestFile?: (uri: string) => Promise<boolean>;
    onDidClasspathUpdate?: vscode.Event<vscode.Uri>;
    onDidProjectsImport?: vscode.Event<vscode.Uri[]>;
    onDidProjectsDelete?: vscode.Event<vscode.Uri[]>;
};

type ClasspathSource = Required<Pick<JavaExtensionApi, 'getClasspaths' | 'isTestFile'>>;

type DaemonState = 'ready' | 'formatting' | 'error';

type Daemon = child_process.ChildProcessByStdio<Writable, Readable, null>;

type Classpath = { classpath: string } | { missing: string };

const REQUEST_TIMEOUT_MS = 10_000;

const CLASSPATH_TIMEOUT_MS = 2_000;

const OWNER_EXECUTE = 0o100;

const JAVA_EXTENSION_ID = 'redhat.java';

let session: Session | undefined;

export function activate(context: vscode.ExtensionContext): void {
    session = new Session(context);
    session.start();
}

export function deactivate(): void {
    session?.stop();
    session = undefined;
}

export function statusFor(
    state: DaemonState,
    detail?: string,
): { detail: string; severity: vscode.LanguageStatusSeverity; busy: boolean } {
    switch (state) {
        case 'ready':
            return { detail: 'ready', severity: vscode.LanguageStatusSeverity.Information, busy: false };
        case 'formatting':
            return { detail: 'formatting', severity: vscode.LanguageStatusSeverity.Information, busy: true };
        case 'error':
            return { detail: detail ?? 'error', severity: vscode.LanguageStatusSeverity.Error, busy: false };
    }
}

export function toDiagnostics(diagnostics: Diagnostic[]): vscode.Diagnostic[] {
    return diagnostics.map((d) => {
        const pos = new vscode.Position(Math.max(0, d.line - 1), Math.max(0, d.column - 1));
        const severity = d.severity === Severity.WARNING
            ? vscode.DiagnosticSeverity.Warning
            : vscode.DiagnosticSeverity.Error;
        const diagnostic = new vscode.Diagnostic(new vscode.Range(pos, pos), d.message, severity);
        diagnostic.source = 'javafmt';
        diagnostic.code = (Kind[d.kind] ?? String(d.kind)).toLowerCase();
        return diagnostic;
    });
}

function messageOf(err: unknown): string {
    return err instanceof Error ? err.message : String(err);
}

export type DaemonEnvironment = {
    extensionPath: string;
    searchPath: string | undefined;
    platform: string;
    arch: string;
    isExecutable: (file: string) => boolean;
};

export function vsceTarget(platform: string, arch: string): string {
    const normalizedArch = arch === 'arm64' ? 'arm64' : 'x64';
    if (platform === 'darwin') return `darwin-${normalizedArch}`;
    if (platform === 'linux') return `linux-${normalizedArch}`;
    if (platform === 'win32') return 'win32-x64';
    return '';
}

function bundledDirectory(extensionPath: string, platform: string, arch: string): string | undefined {
    const target = vsceTarget(platform, arch);
    return target ? path.join(extensionPath, 'bin', target) : undefined;
}

export function makeBundledBinaryExecutable(extensionPath: string, platform: string, arch: string): void {
    const directory = bundledDirectory(extensionPath, platform, arch);
    if (!directory || platform === 'win32') {
        return;
    }
    const bundled = path.join(directory, 'javafmt');
    try {
        const stats = fs.statSync(bundled);
        if (stats.isFile() && !isExecutableFile(bundled)) {
            fs.chmodSync(bundled, stats.mode | OWNER_EXECUTE);
        }
    } catch {}
}

function isExecutableFile(file: string): boolean {
    try {
        fs.accessSync(file, fs.constants.X_OK);
        return fs.statSync(file).isFile();
    } catch {
        return false;
    }
}

export function resolveDaemonBinary(binaryPath: string, env: DaemonEnvironment): { binary: string } | { missing: string } {
    if (binaryPath) {
        return env.isExecutable(binaryPath)
            ? { binary: binaryPath }
            : { missing: `configured javafmt binary not found or not executable at ${binaryPath}` };
    }
    const names = env.platform === 'win32' ? ['javafmt.exe', 'javafmt'] : ['javafmt'];
    const bundledDirs = [bundledDirectory(env.extensionPath, env.platform, env.arch)].filter((dir) => dir !== undefined);
    const pathDirs = (env.searchPath ?? '').split(path.delimiter).filter((dir) => dir !== '');
    const found = [...bundledDirs, ...pathDirs]
        .flatMap((dir) => names.map((name) => path.join(dir, name)))
        .find((candidate) => env.isExecutable(candidate));
    return found ? { binary: found } : { missing: 'no javafmt binary found — set javafmt.binaryPath, or put javafmt on your PATH' };
}

function javaExtension(): { api: JavaExtensionApi; source: ClasspathSource } | { missing: string } {
    const extension = vscode.extensions.getExtension<JavaExtensionApi | undefined>(JAVA_EXTENSION_ID);
    if (!extension) {
        return { missing: `the Java extension (${JAVA_EXTENSION_ID}) is not installed` };
    }
    const api = extension.exports;
    if (!extension.isActive || !api) {
        return { missing: `the Java extension (${JAVA_EXTENSION_ID}) is not active` };
    }
    if (api.serverMode !== 'Standard') {
        return { missing: `the Java extension runs its language server in ${api.serverMode ?? 'an unknown'} mode, which has no classpath` };
    }
    const { getClasspaths, isTestFile } = api;
    if (!getClasspaths || !isTestFile) {
        return { missing: `this version of the Java extension (${JAVA_EXTENSION_ID}) does not give classpaths to other extensions` };
    }
    return { api, source: { getClasspaths, isTestFile } };
}

async function lookUpClasspath(source: ClasspathSource, uri: vscode.Uri): Promise<Classpath> {
    try {
        const scope = (await source.isTestFile(uri.toString())) ? 'test' : 'runtime';
        const found = await source.getClasspaths(uri.toString(), { scope });
        return { classpath: [...found.classpaths, ...found.modulepaths].join(path.delimiter) };
    } catch (err) {
        return { missing: `the Java extension could not give the classpath of ${uri.fsPath}: ${messageOf(err)}` };
    }
}

function within(lookup: Promise<Classpath>, timeoutMs: number): Promise<Classpath> {
    return new Promise((resolve) => {
        const timer = setTimeout(() => resolve({ missing: `the Java extension did not give the classpath within ${timeoutMs / 1000}s` }), timeoutMs);
        void lookup.then((found) => {
            clearTimeout(timer);
            resolve(found);
        });
    });
}

class Session {
    private daemon: Daemon | null = null;

    private readonly pending = new Map<number, PendingRequest>();

    private lastId = 0;

    private reportedMissingBinary = false;

    private reportedMissingClasspath = false;

    private readonly classpaths = new Map<string, Promise<Classpath>>();

    private watchedJavaExtension: JavaExtensionApi | undefined;

    private readonly output: vscode.LogOutputChannel;

    private readonly diagnostics: vscode.DiagnosticCollection;

    private readonly statusItem: vscode.LanguageStatusItem;

    constructor(private readonly context: vscode.ExtensionContext) {
        this.output = vscode.window.createOutputChannel('javafmt', { log: true });
        this.diagnostics = vscode.languages.createDiagnosticCollection('javafmt');
        this.statusItem = vscode.languages.createLanguageStatusItem('javafmt', { language: 'java' });
        this.statusItem.name = 'javafmt';
        this.statusItem.text = 'javafmt';
        this.statusItem.command = { title: 'Restart daemon', command: 'javafmt.restartDaemon' };
    }

    register(): void {
        const provider = {
            provideDocumentFormattingEdits: (doc: vscode.TextDocument) => this.getFormattingEdits(doc),
            provideDocumentRangeFormattingEdits: (doc: vscode.TextDocument) => this.getFormattingEdits(doc),
        };
        this.context.subscriptions.push(
            this.output,
            this.diagnostics,
            this.statusItem,
            vscode.commands.registerCommand('javafmt.restartDaemon', () => {
                if (this.restartDaemon()) {
                    void vscode.window.showInformationMessage('javafmt daemon restarted.');
                }
            }),
            vscode.workspace.onDidCloseTextDocument((doc) => this.diagnostics.delete(doc.uri)),
            vscode.workspace.onDidChangeConfiguration((e) => {
                if (e.affectsConfiguration('javafmt')) {
                    this.restartDaemon();
                }
            }),
            vscode.languages.registerDocumentFormattingEditProvider('java', provider),
            vscode.languages.registerDocumentRangeFormattingEditProvider('java', provider),
        );
    }

    start(): void {
        this.register();
        this.startDaemon();
    }

    stop(): void {
        this.stopDaemon();
    }

    private setStatus(state: DaemonState, detail?: string): void {
        Object.assign(this.statusItem, statusFor(state, detail));
    }

    async getFormattingEdits(doc: vscode.TextDocument): Promise<vscode.TextEdit[]> {
        if (!vscode.workspace.getConfiguration('javafmt').get<boolean>('enable', true)) return [];
        if (!this.daemon) this.startDaemon();
        if (!this.daemon) return [];
        this.setStatus('formatting');
        const source = doc.getText();
        const file = doc.uri.scheme === 'file' ? doc.uri.fsPath : undefined;
        const lookup = file === undefined ? undefined : this.classpathFor(doc.uri);
        const classpath = lookup === undefined ? undefined : await lookup;
        const proc = this.daemon;
        if (!proc) return [];
        let reply: FormatReply;
        try {
            reply = await this.sendRequest(proc, source, { path: file, classpath });
        } catch (err) {
            const message = messageOf(err);
            this.setStatus('error', message);
            this.diagnostics.delete(doc.uri);
            void vscode.window.showErrorMessage(`javafmt: ${message}`);
            return [];
        }
        this.setStatus('ready');
        this.diagnostics.set(doc.uri, toDiagnostics(reply.diagnostics));
        if (reply.output === source) return [];
        const fullRange = new vscode.Range(
            new vscode.Position(0, 0),
            doc.lineAt(doc.lineCount - 1).range.end
        );
        return [vscode.TextEdit.replace(fullRange, reply.output)];
    }

    private startDaemon(): void {
        const binaryPath = vscode.workspace.getConfiguration('javafmt').get<string>('binaryPath', '');

        makeBundledBinaryExecutable(this.context.extensionPath, process.platform, process.arch);

        const resolved = resolveDaemonBinary(binaryPath, {
            extensionPath: this.context.extensionPath,
            searchPath: process.env.PATH,
            platform: process.platform,
            arch: process.arch,
            isExecutable: isExecutableFile,
        });
        if ('missing' in resolved) {
            this.setStatus('error', resolved.missing);
            if (!this.reportedMissingBinary) {
                this.reportedMissingBinary = true;
                void vscode.window.showErrorMessage(`javafmt: ${resolved.missing}`);
            }
            return;
        }

        const proc = child_process.spawn(resolved.binary, ['--daemon'], { stdio: ['pipe', 'pipe', 'ignore'] });

        this.daemon = proc;
        this.setStatus('ready');

        void this.readReplies(proc);

        proc.on('exit', (code: number | null, signal: NodeJS.Signals | null) => this.daemonGone(proc, signal ? `daemon exited with signal ${signal}` : `daemon exited with code ${code}`));
        proc.on('error', (err: Error) => this.daemonGone(proc, `daemon failed: ${err.message}`));
        proc.stdin.on('error', (err: Error) => this.daemonGone(proc, `daemon input failed: ${err.message}`));
    }

    private async readReplies(proc: Daemon): Promise<void> {
        try {
            for await (const reply of decodeReplies(proc.stdout)) {
                const request = this.pending.get(reply.id);
                if (request && this.daemon === proc) {
                    this.pending.delete(reply.id);
                    request.resolve(reply);
                }
            }
        } catch {
            this.daemonGone(proc, 'daemon sent a reply that is not part of its protocol');
        }
    }

    private restartDaemon(): boolean {
        this.reportedMissingBinary = false;
        this.stopDaemon();
        this.startDaemon();
        return this.daemon !== null;
    }

    private stopDaemon(reason = 'daemon stopped'): void {
        const proc = this.daemon;
        this.daemon = null;
        proc?.kill();
        const err = new Error(reason);
        for (const request of this.pending.values()) {
            request.reject(err);
        }
        this.pending.clear();
    }

    private daemonGone(proc: child_process.ChildProcess, reason: string): void {
        if (this.daemon !== proc) {
            return;
        }
        this.stopDaemon(reason);
        this.setStatus('error', reason);
    }

    private classpathFor(uri: vscode.Uri): Promise<string | undefined> | undefined {
        const java = javaExtension();
        if ('missing' in java) {
            this.reportMissingClasspath(java.missing);
            return undefined;
        }
        this.watchClasspathChanges(java.api);
        const directory = path.dirname(uri.fsPath);
        let lookup = this.classpaths.get(directory);
        if (!lookup) {
            lookup = lookUpClasspath(java.source, uri);
            this.classpaths.set(directory, lookup);
        }
        return within(lookup, CLASSPATH_TIMEOUT_MS).then((found) => {
            if ('missing' in found) {
                this.reportMissingClasspath(found.missing);
                return undefined;
            }
            this.reportedMissingClasspath = false;
            return found.classpath;
        });
    }

    private watchClasspathChanges(api: JavaExtensionApi): void {
        if (this.watchedJavaExtension === api) {
            return;
        }
        this.watchedJavaExtension = api;
        for (const event of [api.onDidClasspathUpdate, api.onDidProjectsImport, api.onDidProjectsDelete]) {
            if (event) {
                this.context.subscriptions.push(event(() => this.classpaths.clear()));
            }
        }
    }

    private reportMissingClasspath(reason: string): void {
        if (this.reportedMissingClasspath) {
            return;
        }
        this.reportedMissingClasspath = true;
        this.output.info(`javafmt formats without the classpath, so the rules that need it do not run: ${reason}`);
    }

    private sendRequest(proc: Daemon, source: string, scope: FileScope): Promise<FormatReply> {
        return new Promise((resolve, reject) => {
            this.lastId = this.lastId >= 0xffffffff ? 1 : this.lastId + 1;
            const id = this.lastId;
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new Error(`did not respond within ${REQUEST_TIMEOUT_MS / 1000}s`));
                if (this.daemon === proc) {
                    this.stopDaemon();
                }
            }, REQUEST_TIMEOUT_MS);
            this.pending.set(id, {
                resolve: (response) => {
                    clearTimeout(timer);
                    resolve(response);
                },
                reject: (err) => {
                    clearTimeout(timer);
                    reject(err);
                },
            });
            proc.stdin.write(encodeRequest(id, source, scope));
        });
    }
}
