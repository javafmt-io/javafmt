import * as vscode from 'vscode';
import * as child_process from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { Diagnostic, FormatReply, Kind, Severity, decodeReplies, encodeRequest } from './protocol';

type PendingRequest = {
    resolve: (reply: FormatReply) => void;
    reject: (err: Error) => void;
};

let ctx: vscode.ExtensionContext;
let daemon: child_process.ChildProcess | null = null;
const pending = new Map<number, PendingRequest>();
let lastId = 0;
let diagnosticCollection: vscode.DiagnosticCollection;
let statusItem: vscode.LanguageStatusItem;
let reportedMissingBinary = false;

const REQUEST_TIMEOUT_MS = 10_000;

export function activate(context: vscode.ExtensionContext): void {
    ctx = context;
    reportedMissingBinary = false;

    diagnosticCollection = vscode.languages.createDiagnosticCollection('javafmt');
    ctx.subscriptions.push(diagnosticCollection);

    statusItem = vscode.languages.createLanguageStatusItem('javafmt', { language: 'java' });
    statusItem.name = 'javafmt';
    statusItem.text = 'javafmt';
    statusItem.command = { title: 'Restart daemon', command: 'javafmt.restartDaemon' };
    ctx.subscriptions.push(statusItem);

    ctx.subscriptions.push(
        vscode.commands.registerCommand('javafmt.restartDaemon', () => {
            restartDaemon();
            void vscode.window.showInformationMessage('javafmt daemon restarted.');
        })
    );

    ctx.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration('javafmt')) {
                restartDaemon();
            }
        })
    );

    ctx.subscriptions.push(
        vscode.languages.registerDocumentFormattingEditProvider('java', {
            provideDocumentFormattingEdits(doc: vscode.TextDocument): Promise<vscode.TextEdit[]> {
                return getFormattingEdits(doc);
            },
        })
    );

    // javafmt is a whole-file formatter; range formatting formats the full document.
    ctx.subscriptions.push(
        vscode.languages.registerDocumentRangeFormattingEditProvider('java', {
            provideDocumentRangeFormattingEdits(doc: vscode.TextDocument, _range: vscode.Range): Promise<vscode.TextEdit[]> {
                return getFormattingEdits(doc);
            },
        })
    );

    startDaemon();
}

export function deactivate(): void {
    stopDaemon();
}

// Exported for testing.
export function statusFor(
    state: 'ready' | 'formatting' | 'error',
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

// Exported for testing.
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

function setStatus(state: 'ready' | 'formatting' | 'error', detail?: string): void {
    Object.assign(statusItem, statusFor(state, detail));
}

async function getFormattingEdits(doc: vscode.TextDocument): Promise<vscode.TextEdit[]> {
    if (!vscode.workspace.getConfiguration('javafmt').get<boolean>('enable', true)) return [];
    if (!daemon) {
        startDaemon();
        if (!daemon) return [];
    }
    setStatus('formatting');
    const source = doc.getText();
    let reply: FormatReply;
    try {
        reply = await sendRequest(source);
    } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        setStatus('error', msg);
        void vscode.window.showErrorMessage(`javafmt: ${msg}`);
        return [];
    }
    setStatus('ready');
    diagnosticCollection.set(doc.uri, toDiagnostics(reply.diagnostics));
    if (reply.output === source) return [];
    const fullRange = new vscode.Range(
        new vscode.Position(0, 0),
        doc.lineAt(doc.lineCount - 1).range.end
    );
    return [vscode.TextEdit.replace(fullRange, reply.output)];
}

function startDaemon(): void {
    const binaryPath = vscode.workspace.getConfiguration('javafmt').get<string>('binaryPath', '');

    makeBundledBinaryExecutable(ctx.extensionPath, process.platform, process.arch);

    let daemonCommand: DaemonCommand;
    try {
        daemonCommand = resolveDaemonCommand(binaryPath, {
            extensionPath: ctx.extensionPath,
            searchPath: process.env.PATH,
            platform: process.platform,
            arch: process.arch,
            isExecutable: isExecutableFile,
        });
    } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        setStatus('error', msg);
        if (!reportedMissingBinary) {
            reportedMissingBinary = true;
            void vscode.window.showErrorMessage(`javafmt: ${msg}`);
        }
        return;
    }

    const proc = child_process.spawn(daemonCommand.command, daemonCommand.args, { stdio: ['pipe', 'pipe', 'pipe'] });

    daemon = proc;
    setStatus('ready');

    const gone = (reason: string): void => {
        if (daemon !== proc) {
            return;
        }
        daemon = null;
        proc.kill();
        setStatus('error', reason);
        const err = new Error(`javafmt ${reason}`);
        for (const req of pending.values()) {
            req.reject(err);
        }
        pending.clear();
    };

    void (async () => {
        try {
            for await (const reply of decodeReplies(proc.stdout!)) {
                const req = pending.get(reply.id);
                if (req) {
                    pending.delete(reply.id);
                    req.resolve(reply);
                }
            }
        } catch {
            gone('daemon sent a reply that is not part of its protocol');
        }
    })();

    // slf4j-simple writes to stderr; discard to avoid clogging the pipe
    proc.stderr!.on('data', (_chunk: Buffer) => {});

    proc.on('exit', (code: number | null) => gone(`daemon exited with code ${code}`));
    proc.on('error', (err: Error) => gone(`daemon failed: ${err.message}`));
    proc.stdin!.on('error', (err: Error) => gone(`daemon input failed: ${err.message}`));
}

function restartDaemon(): void {
    reportedMissingBinary = false;
    stopDaemon();
    startDaemon();
}

function stopDaemon(): void {
    if (daemon) {
        daemon.kill();
        daemon = null;
    }
    const err = new Error('javafmt daemon stopped');
    for (const req of pending.values()) {
        req.reject(err);
    }
    pending.clear();
}

export type DaemonCommand = { command: string; args: string[] };

export type DaemonEnvironment = {
    extensionPath: string;
    searchPath: string | undefined;
    platform: string;
    arch: string;
    isExecutable: (file: string) => boolean;
};

// Exported for testing.
export function vsceTarget(platform: string, arch: string): string {
    const normalizedArch = arch === 'arm64' ? 'arm64' : 'x64';
    if (platform === 'darwin') return `darwin-${normalizedArch}`;
    if (platform === 'linux') return `linux-${normalizedArch}`;
    if (platform === 'win32') return 'win32-x64';
    return '';
}

function makeBundledBinaryExecutable(extensionPath: string, platform: string, arch: string): void {
    const target = vsceTarget(platform, arch);
    if (!target || platform === 'win32') {
        return;
    }
    const bundled = path.join(extensionPath, 'bin', target, 'javafmt');
    try {
        if (fs.statSync(bundled).isFile() && !isExecutableFile(bundled)) {
            fs.chmodSync(bundled, fs.statSync(bundled).mode | fs.constants.S_IXUSR);
        }
    } catch {
        return;
    }
}

function isExecutableFile(file: string): boolean {
    try {
        fs.accessSync(file, fs.constants.X_OK);
        return fs.statSync(file).isFile();
    } catch {
        return false;
    }
}

// Exported for testing.
export function resolveDaemonCommand(binaryPath: string, env: DaemonEnvironment): DaemonCommand {
    if (binaryPath) {
        if (!env.isExecutable(binaryPath)) {
            throw new Error(`configured javafmt binary not found or not executable at ${binaryPath}`);
        }
        return { command: binaryPath, args: ['--daemon'] };
    }
    const names = env.platform === 'win32' ? ['javafmt.exe', 'javafmt'] : ['javafmt'];
    const target = vsceTarget(env.platform, env.arch);
    const bundledDirs = target ? [path.join(env.extensionPath, 'bin', target)] : [];
    const pathDirs = (env.searchPath ?? '').split(path.delimiter).filter((dir) => dir !== '');
    const found = [...bundledDirs, ...pathDirs]
        .flatMap((dir) => names.map((name) => path.join(dir, name)))
        .find((candidate) => env.isExecutable(candidate));
    if (!found) {
        throw new Error('no javafmt binary found — set javafmt.binaryPath, or put javafmt on your PATH');
    }
    return { command: found, args: ['--daemon'] };
}

function sendRequest(source: string): Promise<FormatReply> {
    const proc = daemon!;
    return new Promise((resolve, reject) => {
        lastId = lastId >= 0xffffffff ? 1 : lastId + 1;
        const id = lastId;
        const timer = setTimeout(() => {
            pending.delete(id);
            reject(new Error(`javafmt did not respond within ${REQUEST_TIMEOUT_MS / 1000}s`));
            if (daemon === proc) {
                stopDaemon();
            }
        }, REQUEST_TIMEOUT_MS);
        pending.set(id, {
            resolve: (response) => {
                clearTimeout(timer);
                resolve(response);
            },
            reject: (err) => {
                clearTimeout(timer);
                reject(err);
            },
        });
        proc.stdin!.write(encodeRequest(id, source));
    });
}
