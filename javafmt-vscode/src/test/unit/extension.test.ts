import { activate, toDiagnostics, isJavaDocument, statusFor } from '../extension';
import { DiagnosticSeverity, Position, mockStatusBar, setActiveTextEditor } from '../__mocks__/vscode';
import * as vscode from 'vscode';
import type * as vscodeTypes from 'vscode';

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

describe('after activate()', () => {
    let onChangeEditor: () => void;
    let onWillSave: (e: { document: { languageId: string }; waitUntil: jest.Mock }) => void;

    beforeEach(() => {
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
            const waitUntil = jest.fn();
            onWillSave({ document: { languageId: 'java' }, waitUntil });
            expect(waitUntil).toHaveBeenCalledWith(expect.any(Promise));
        });
    });
});
