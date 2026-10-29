import { activate, deactivate, toDiagnostics, isJavaDocument, statusFor, buildRequest } from '../../extension';
import * as child_process from 'child_process';
import { EventEmitter } from 'events';
import * as fs from 'fs';
import { PassThrough } from 'stream';
import { DiagnosticSeverity, Position, mockStatusBar, setActiveTextEditor } from '../../__mocks__/vscode';
import * as vscode from 'vscode';
import type * as vscodeTypes from 'vscode';

describe('buildRequest', () => {
    it('echoes the id and source into the payload', () => {
        const payload = buildRequest('abc', 'class B {}') as Record<string, unknown>;
        expect(payload.id).toBe('abc');
        expect(payload.source).toBe('class B {}');
    });
});

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

describe('isJavaDocument', () => {
    it('returns true for java', () => expect(isJavaDocument('java')).toBe(true));
    it('returns false for other languages', () => expect(isJavaDocument('kotlin')).toBe(false));
    it('returns false for undefined (no active editor)', () => expect(isJavaDocument(undefined)).toBe(false));
});

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

function makeContext(prefix = ''): vscodeTypes.ExtensionContext {
    return {
        subscriptions: { push: jest.fn() },
        extensionPath: prefix,
        asAbsolutePath: (p: string) => `${prefix}${p}`,
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

    describe('registerDocumentRangeFormattingEditProvider', () => {
        it('registers a range formatting provider for java', () => {
            expect(vscode.languages.registerDocumentRangeFormattingEditProvider)
                .toHaveBeenCalledWith('java', expect.any(Object));
        });
    });

    // Spies are created inside each test (after the outer beforeEach's activate() runs) so
    // the module-level spawn mock does not pre-count calls from the outer beforeEach setup.
    describe('startDaemon spawn behavior', () => {
        afterEach(() => {
            jest.restoreAllMocks();
            deactivate();
        });

        it('spawns the native binary directly (no java -jar) when the platform binary exists', () => {
            const spawnSpy = jest.spyOn(child_process, 'spawn').mockImplementation(() => mockProcess());
            jest.spyOn(fs, 'existsSync').mockImplementation((p) => !String(p).endsWith('.jar'));
            deactivate();
            activate(makeContext());
            expect(spawnSpy).toHaveBeenCalledWith(
                expect.stringContaining('javafmt-daemon'),
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
                expect.stringContaining('no native daemon'),
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
