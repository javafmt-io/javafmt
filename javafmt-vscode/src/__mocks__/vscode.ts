// Minimal VS Code API stubs for unit tests. Only the surface used by extension.ts.

export enum DiagnosticSeverity {
    Error = 0,
    Warning = 1,
    Information = 2,
    Hint = 3,
}

export enum StatusBarAlignment {
    Left = 1,
    Right = 2,
}

export class Position {
    constructor(
        public readonly line: number,
        public readonly character: number,
    ) {}
}

export class Range {
    constructor(
        public readonly start: Position,
        public readonly end: Position,
    ) {}
}

export class Diagnostic {
    constructor(
        public readonly range: Range,
        public readonly message: string,
        public readonly severity: DiagnosticSeverity,
    ) {}
}

// Shared status bar instance — tests import this to inspect text/show/hide.
export const mockStatusBar = {
    text: '',
    tooltip: '',
    command: undefined as string | undefined,
    show: jest.fn(),
    hide: jest.fn(),
    dispose: jest.fn(),
};

// Settable active editor — tests call setActiveTextEditor() before triggering
// the onDidChangeActiveTextEditor callback to control syncStatusBar behaviour.
let _activeEditor: { document: { languageId: string } } | undefined;
export function setActiveTextEditor(e: typeof _activeEditor): void {
    _activeEditor = e;
}

export const window = {
    createStatusBarItem: jest.fn(() => mockStatusBar),
    get activeTextEditor() { return _activeEditor; },
    onDidChangeActiveTextEditor: jest.fn(() => ({ dispose: jest.fn() })),
    showInformationMessage: jest.fn(),
    showErrorMessage: jest.fn(),
};

export const workspace = {
    getConfiguration: jest.fn(() => ({
        get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
    })),
    onWillSaveTextDocument: jest.fn(() => ({ dispose: jest.fn() })),
};

export const languages = {
    createDiagnosticCollection: jest.fn(() => ({ set: jest.fn(), dispose: jest.fn() })),
    registerDocumentFormattingEditProvider: jest.fn(() => ({ dispose: jest.fn() })),
    registerDocumentRangeFormattingEditProvider: jest.fn(() => ({ dispose: jest.fn() })),
};

export const commands = {
    registerCommand: jest.fn(() => ({ dispose: jest.fn() })),
};
