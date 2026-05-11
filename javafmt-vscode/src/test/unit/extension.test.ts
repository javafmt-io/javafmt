import { activate, deactivate, toDiagnostics, isJavaDocument, statusFor, buildRequest, nativeBinaryName, resolveDaemonExecutable } from '../../extension';
import * as child_process from 'child_process';
import { EventEmitter } from 'events';
import * as fs from 'fs';
import { PassThrough } from 'stream';
import { DiagnosticSeverity, Position, mockStatusBar, setActiveTextEditor } from '../../__mocks__/vscode';
import * as vscode from 'vscode';
import type * as vscodeTypes from 'vscode';

// ---------------------------------------------------------------------------
// buildRequest
// ---------------------------------------------------------------------------

describe('buildRequest', () => {
    it('includes config.reorderMembers=true when specified', () => {
        const payload = buildRequest('id1', 'class A {}', true) as Record<string, unknown>;
        expect((payload.config as Record<string, unknown>).reorderMembers).toBe(true);
    });

    it('includes config.reorderMembers=false when specified', () => {
        const payload = buildRequest('id1', 'class A {}', false) as Record<string, unknown>;
        expect((payload.config as Record<string, unknown>).reorderMembers).toBe(false);
    });

    it('echoes the id and source into the payload', () => {
        const payload = buildRequest('abc', 'class B {}', false) as Record<string, unknown>;
        expect(payload.id).toBe('abc');
        expect(payload.source).toBe('class B {}');
    });
});

// ---------------------------------------------------------------------------
// toDiagnostics
// ---------------------------------------------------------------------------

describe('toDiagnostics', () => {
    it('returns an empty array for no diagnostics', () => {
        expect(toDiagnostics([])).toEqual([]);
    });

    it('converts 1-based line/col to 0-based vscode.Position', () => {
        const [d] = toDiagnostics([{ severity: 'warning', message: 'FallThrough', line: 3, col: 5 }]);
        expect(d.range.start).toEqual(new Position(2, 4));
    });

    it('clamps unknown position (line=0, col=0) to Position(0, 0)', () => {
        const [d] = toDiagnostics([{ severity: 'error', message: 'parse error', line: 0, col: 0 }]);
        expect(d.range.start).toEqual(new Position(0, 0));
    });

    it('maps severity "error" to DiagnosticSeverity.Error', () => {
        const [d] = toDiagnostics([{ severity: 'error', message: 'e', line: 1, col: 1 }]);
        expect(d.severity).toBe(DiagnosticSeverity.Error);
    });

    it('maps severity "warning" to DiagnosticSeverity.Warning', () => {
        const [d] = toDiagnostics([{ severity: 'warning', message: 'w', line: 1, col: 1 }]);
        expect(d.severity).toBe(DiagnosticSeverity.Warning);
    });

    it('sets range.start === range.end (zero-width point diagnostic)', () => {
        const [d] = toDiagnostics([{ severity: 'warning', message: 'w', line: 5, col: 10 }]);
        expect(d.range.start).toEqual(d.range.end);
    });

    it('preserves the diagnostic message verbatim', () => {
        const [d] = toDiagnostics([{ severity: 'warning', message: 'EqualsHashCode', line: 1, col: 1 }]);
        expect(d.message).toBe('EqualsHashCode');
    });
});

// ---------------------------------------------------------------------------
// isJavaDocument
// ---------------------------------------------------------------------------

describe('isJavaDocument', () => {
    it('returns true for java', () => expect(isJavaDocument('java')).toBe(true));
    it('returns false for other languages', () => expect(isJavaDocument('kotlin')).toBe(false));
    it('returns false for undefined (no active editor)', () => expect(isJavaDocument(undefined)).toBe(false));
});

// ---------------------------------------------------------------------------
// statusFor
// ---------------------------------------------------------------------------

describe('statusFor', () => {
    it('ready: correct text and tooltip', () => {
        const { text, tooltip } = statusFor('ready');
        expect(text).toBe('$(check) javafmt');
        expect(tooltip).toBe('javafmt: ready — click to restart daemon');
    });

    it('formatting: correct text and tooltip', () => {
        const { text, tooltip } = statusFor('formatting');
        expect(text).toBe('$(loading~spin) javafmt');
        expect(tooltip).toBe('javafmt: formatting…');
    });

    it('error with detail: embeds detail in tooltip', () => {
        const { text, tooltip } = statusFor('error', 'daemon crashed');
        expect(text).toBe('$(error) javafmt');
        expect(tooltip).toBe('javafmt: daemon crashed');
    });

    it('error without detail: falls back to "error"', () => {
        expect(statusFor('error').tooltip).toBe('javafmt: error');
    });
});

// ---------------------------------------------------------------------------
// syncStatusBar and formatOnSave guard — exercised through activate()
// ---------------------------------------------------------------------------

function makeContext(): vscodeTypes.ExtensionContext {
    return {
        subscriptions: { push: jest.fn() },
        asAbsolutePath: (p: string) => p,
    } as unknown as vscodeTypes.ExtensionContext;
}

function mockProcess(): child_process.ChildProcess {
    return Object.assign(new EventEmitter(), {
        stdin: { write: jest.fn() },
        stdout: new PassThrough(),
        stderr: new PassThrough(),
        kill: jest.fn(),
    }) as unknown as child_process.ChildProcess;
}

describe('after activate()', () => {
    let onChangeEditor: () => void;
    let onWillSave: (e: { document: { languageId: string }; waitUntil: jest.Mock }) => void;

    beforeEach(() => {
        deactivate();
        activate(makeContext());
        // Clear counts from activate() itself so assertions start from zero.
        mockStatusBar.show.mockClear();
        mockStatusBar.hide.mockClear();
        onChangeEditor = (vscode.window.onDidChangeActiveTextEditor as jest.Mock).mock.calls[0][0] as () => void;
        onWillSave = (vscode.workspace.onWillSaveTextDocument as jest.Mock).mock.calls[0][0] as typeof onWillSave;
    });

    afterEach(() => {
        setActiveTextEditor(undefined);
    });

    describe('syncStatusBar', () => {
        it('shows the status bar when the active editor is Java', () => {
            setActiveTextEditor({ document: { languageId: 'java' } });
            onChangeEditor();
            expect(mockStatusBar.show).toHaveBeenCalledTimes(1);
            expect(mockStatusBar.hide).not.toHaveBeenCalled();
        });

        it('hides the status bar for non-Java editors', () => {
            setActiveTextEditor({ document: { languageId: 'kotlin' } });
            onChangeEditor();
            expect(mockStatusBar.hide).toHaveBeenCalledTimes(1);
            expect(mockStatusBar.show).not.toHaveBeenCalled();
        });

        it('hides the status bar when there is no active editor', () => {
            setActiveTextEditor(undefined);
            onChangeEditor();
            expect(mockStatusBar.hide).toHaveBeenCalledTimes(1);
            expect(mockStatusBar.show).not.toHaveBeenCalled();
        });
    });

    describe('nativeBinaryName', () => {
        it('maps darwin/arm64 to darwin-arm64 binary', () => {
            expect(nativeBinaryName('darwin', 'arm64')).toBe('javafmt-daemon-darwin-arm64');
        });

        it('maps linux/x64 to linux-amd64 binary', () => {
            expect(nativeBinaryName('linux', 'x64')).toBe('javafmt-daemon-linux-amd64');
        });

        it('maps win32/x64 to windows-amd64.exe binary', () => {
            expect(nativeBinaryName('win32', 'x64')).toBe('javafmt-daemon-windows-amd64.exe');
        });

        it('returns empty string for unsupported platform', () => {
            expect(nativeBinaryName('freebsd', 'x64')).toBe('');
        });
    });

    describe('resolveDaemonExecutable', () => {
        function makeCtx(): vscodeTypes.ExtensionContext {
            return {
                subscriptions: { push: jest.fn() },
                asAbsolutePath: (p: string) => `/ext/${p}`,
            } as unknown as vscodeTypes.ExtensionContext;
        }

        afterEach(() => jest.restoreAllMocks());

        it('returns native when the platform binary exists', () => {
            const name = nativeBinaryName(process.platform, process.arch);
            if (!name) return; // skip on unsupported platforms
            jest.spyOn(fs, 'existsSync').mockImplementation((p) => p === `/ext/bin/${name}`);
            const result = resolveDaemonExecutable(makeCtx());
            expect(result).toEqual({ kind: 'native', path: `/ext/bin/${name}` });
        });

        it('falls back to jar when no native binary exists', () => {
            jest.spyOn(fs, 'existsSync').mockImplementation((p) => String(p).endsWith('.jar'));
            const result = resolveDaemonExecutable(makeCtx());
            expect(result).toEqual({ kind: 'jar', path: '/ext/bin/javafmt-daemon.jar' });
        });

        it('returns missing when neither native binary nor jar exists', () => {
            jest.spyOn(fs, 'existsSync').mockReturnValue(false);
            expect(resolveDaemonExecutable(makeCtx())).toEqual({ kind: 'missing' });
        });

        it('prefers native over jar when both exist', () => {
            const name = nativeBinaryName(process.platform, process.arch);
            if (!name) return; // skip on unsupported platforms
            jest.spyOn(fs, 'existsSync').mockReturnValue(true);
            const result = resolveDaemonExecutable(makeCtx());
            expect(result.kind).toBe('native');
        });
    });

    describe('registerDocumentRangeFormattingEditProvider', () => {
        it('registers a range formatting provider for java', () => {
            expect(vscode.languages.registerDocumentRangeFormattingEditProvider)
                .toHaveBeenCalledWith('java', expect.any(Object));
        });
    });

    // ---------------------------------------------------------------------------
    // startDaemon spawn behavior — verified by observing child_process.spawn calls
    // ---------------------------------------------------------------------------

    // Spies are created inside each test (after the outer beforeEach's activate() runs) so
    // the module-level spawn mock does not pre-count calls from the outer beforeEach setup.
    describe('startDaemon spawn behavior', () => {
        afterEach(() => {
            jest.restoreAllMocks();
            deactivate();
        });

        it('spawns the native binary directly (no java -jar) when the platform binary exists', () => {
            const name = nativeBinaryName(process.platform, process.arch);
            if (!name) return; // skip on unsupported CI platforms
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            jest.spyOn(fs, 'existsSync').mockImplementation((p) => String(p).endsWith(name));
            deactivate();
            activate(makeContext());
            expect(spawnSpy).toHaveBeenCalledWith(
                expect.stringContaining(name),
                [],
                expect.any(Object),
            );
        });

        it('spawns via java -jar when only the bundled jar exists', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            jest.spyOn(fs, 'existsSync').mockImplementation((p) => String(p).endsWith('.jar'));
            deactivate();
            activate(makeContext());
            expect(spawnSpy).toHaveBeenCalledWith(
                'java',
                ['-jar', expect.stringContaining('.jar')],
                expect.any(Object),
            );
        });

        it('shows error message when neither native binary nor jar exists', () => {
            jest.spyOn(fs, 'existsSync').mockReturnValue(false);
            deactivate();
            activate(makeContext());
            expect(vscode.window.showErrorMessage).toHaveBeenLastCalledWith(
                expect.stringContaining('daemon not found'),
            );
        });
    });

    describe('formatOnSave guard', () => {
        it('skips non-Java documents regardless of the formatOnSave setting', () => {
            const waitUntil = jest.fn();
            onWillSave({ document: { languageId: 'python' }, waitUntil });
            expect(waitUntil).not.toHaveBeenCalled();
        });

        it('skips Java documents when javafmt.formatOnSave is false (default)', () => {
            const waitUntil = jest.fn();
            onWillSave({ document: { languageId: 'java' }, waitUntil });
            expect(waitUntil).not.toHaveBeenCalled();
        });

        it('calls waitUntil for Java documents when javafmt.formatOnSave is true', () => {
            (vscode.workspace.getConfiguration as jest.Mock).mockReturnValueOnce({
                get: jest.fn(() => true),
            });
            const waitUntil = jest.fn().mockImplementation((p: Promise<unknown>) => p?.catch(() => {}));
            const doc = { languageId: 'java', getText: jest.fn(() => '') };
            onWillSave({ document: doc as { languageId: string }, waitUntil });
            expect(waitUntil).toHaveBeenCalledWith(expect.any(Promise));
        });
    });
});
