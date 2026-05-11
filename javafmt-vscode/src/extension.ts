import * as vscode from 'vscode';
import * as child_process from 'child_process';
import * as readline from 'readline';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { DiagnosticDto, FormatResponse, parseResponse } from './protocol';

type PendingRequest = {
    resolve: (response: FormatResponse) => void;
    reject: (err: Error) => void;
};

let ctx: vscode.ExtensionContext;
let daemon: child_process.ChildProcess | null = null;
const pending = new Map<string, PendingRequest>();
let diagnosticCollection: vscode.DiagnosticCollection;
let statusBar: vscode.StatusBarItem;

export function activate(context: vscode.ExtensionContext): void {
    ctx = context;

    diagnosticCollection = vscode.languages.createDiagnosticCollection('javafmt');
    ctx.subscriptions.push(diagnosticCollection);

    statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    statusBar.command = 'javafmt.restartDaemon';
    ctx.subscriptions.push(statusBar);
    ctx.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(syncStatusBar));
    syncStatusBar();

    ctx.subscriptions.push(
        vscode.commands.registerCommand('javafmt.restartDaemon', () => {
            stopDaemon();
            startDaemon();
            vscode.window.showInformationMessage('javafmt daemon restarted.');
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

    // javafmt.formatOnSave: convenience alias so users don't have to set
    // editor.formatOnSave + [java].editor.defaultFormatter manually.
    ctx.subscriptions.push(
        vscode.workspace.onWillSaveTextDocument((e) => {
            if (e.document.languageId !== 'java') return;
            if (!vscode.workspace.getConfiguration('javafmt').get<boolean>('formatOnSave', false)) return;
            e.waitUntil(getFormattingEdits(e.document));
        })
    );

    startDaemon();
}

export function deactivate(): void {
    stopDaemon();
}

// Exported for testing.
export function isJavaDocument(languageId: string | undefined): boolean {
    return languageId === 'java';
}

// Exported for testing.
export function statusFor(
    state: 'ready' | 'formatting' | 'error',
    detail?: string,
): { text: string; tooltip: string } {
    switch (state) {
        case 'ready':
            return { text: '$(check) javafmt', tooltip: 'javafmt: ready — click to restart daemon' };
        case 'formatting':
            return { text: '$(loading~spin) javafmt', tooltip: 'javafmt: formatting…' };
        case 'error':
            return { text: '$(error) javafmt', tooltip: `javafmt: ${detail ?? 'error'}` };
    }
}

// Exported for testing.
export function toDiagnostics(dtos: DiagnosticDto[]): vscode.Diagnostic[] {
    // DiagnosticDto uses 1-based line/col; vscode.Position is 0-based
    return dtos.map((d) => {
        const line = Math.max(0, d.line - 1);
        const col = Math.max(0, d.col - 1);
        const pos = new vscode.Position(line, col);
        const severity = d.severity === 'error'
            ? vscode.DiagnosticSeverity.Error
            : vscode.DiagnosticSeverity.Warning;
        return new vscode.Diagnostic(new vscode.Range(pos, pos), d.message, severity);
    });
}

function syncStatusBar(): void {
    if (isJavaDocument(vscode.window.activeTextEditor?.document.languageId)) {
        statusBar.show();
    } else {
        statusBar.hide();
    }
}

function setStatus(state: 'ready' | 'formatting' | 'error', detail?: string): void {
    const { text, tooltip } = statusFor(state, detail);
    statusBar.text = text;
    statusBar.tooltip = tooltip;
}

async function getFormattingEdits(doc: vscode.TextDocument): Promise<vscode.TextEdit[]> {
    if (!vscode.workspace.getConfiguration('javafmt').get<boolean>('enable', true)) return [];
    if (!daemon) {
        startDaemon();
        if (!daemon) return [];
    }
    setStatus('formatting');
    const source = doc.getText();
    let response: FormatResponse;
    try {
        response = await sendRequest(source);
    } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        setStatus('error', msg);
        vscode.window.showErrorMessage(`javafmt: ${msg}`);
        return [];
    }
    setStatus('ready');
    diagnosticCollection.set(doc.uri, toDiagnostics(response.diagnostics));
    if (response.output === source) return [];
    const fullRange = new vscode.Range(
        new vscode.Position(0, 0),
        doc.lineAt(doc.lineCount - 1).range.end
    );
    return [vscode.TextEdit.replace(fullRange, response.output)];
}

function startDaemon(): void {
    const config = vscode.workspace.getConfiguration('javafmt');
    const java = config.get<string>('javaExecutable', 'java');
    const jarOverride = config.get<string>('daemonJar', '');
    const jarPath = jarOverride || ctx.asAbsolutePath(path.join('bin', 'javafmt-daemon.jar'));

    if (!fs.existsSync(jarPath)) {
        const msg = `daemon jar not found at ${jarPath}`;
        setStatus('error', msg);
        vscode.window.showErrorMessage(`javafmt: ${msg}. Run 'gradle :javafmt-vscode:build' to build it.`);
        return;
    }

    const proc = child_process.spawn(java, ['-jar', jarPath], {
        stdio: ['pipe', 'pipe', 'pipe'],
    });
    daemon = proc;
    setStatus('ready');

    const rl = readline.createInterface({ input: proc.stdout! });
    rl.on('line', (line: string) => {
        const response = parseResponse(line);
        if (!response) return;
        const req = pending.get(response.id);
        if (req) {
            pending.delete(response.id);
            req.resolve(response);
        }
    });

    // slf4j-simple writes to stderr; discard to avoid clogging the pipe
    proc.stderr!.on('data', (_chunk: Buffer) => {});

    proc.on('exit', (code: number | null) => {
        // Only clear daemon if this process is still the active one (guards against restart races)
        if (daemon === proc) {
            daemon = null;
            setStatus('error', `daemon exited with code ${code}`);
        }
        const err = new Error(`javafmt daemon exited with code ${code}`);
        for (const req of pending.values()) {
            req.reject(err);
        }
        pending.clear();
    });
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

export type DaemonExecutable =
    | { kind: 'native'; path: string }
    | { kind: 'jar'; path: string }
    | { kind: 'missing' };

// Exported for testing.
export function nativeBinaryName(platform: string, arch: string): string {
    if (platform === 'darwin' && arch === 'arm64') return 'javafmt-daemon-darwin-arm64';
    if (platform === 'darwin' && arch === 'x64') return 'javafmt-daemon-darwin-amd64';
    if (platform === 'linux' && arch === 'x64') return 'javafmt-daemon-linux-amd64';
    if (platform === 'win32' && arch === 'x64') return 'javafmt-daemon-windows-amd64.exe';
    return '';
}

// Exported for testing.
export function resolveDaemonExecutable(context: vscode.ExtensionContext): DaemonExecutable {
    const name = nativeBinaryName(process.platform, process.arch);
    if (name) {
        const nativePath = context.asAbsolutePath(path.join('bin', name));
        if (fs.existsSync(nativePath)) {
            return { kind: 'native', path: nativePath };
        }
    }
    const jarPath = context.asAbsolutePath(path.join('bin', 'javafmt-daemon.jar'));
    if (fs.existsSync(jarPath)) {
        return { kind: 'jar', path: jarPath };
    }
    return { kind: 'missing' };
}

// Exported for testing.
export function buildRequest(id: string, source: string, reorderMembers: boolean): object {
    return { id, source, config: { reorderMembers } };
}

function sendRequest(source: string): Promise<FormatResponse> {
    return new Promise((resolve, reject) => {
        const id = crypto.randomUUID();
        pending.set(id, { resolve, reject });
        const reorderMembers = vscode.workspace.getConfiguration('javafmt').get<boolean>('reorderMembers', false);
        daemon!.stdin!.write(JSON.stringify(buildRequest(id, source, reorderMembers)) + '\n');
    });
}
