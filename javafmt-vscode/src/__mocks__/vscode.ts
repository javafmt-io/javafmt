// Minimal VS Code API stubs for unit tests. Only the surface used by extension.ts.

export enum DiagnosticSeverity {
    Error = 0,
    Warning = 1,
    Information = 2,
    Hint = 3,
}

export enum LanguageStatusSeverity {
    Information = 0,
    Warning = 1,
    Error = 2,
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
    source?: string;

    code?: string | number;

    constructor(
        public readonly range: Range,
        public readonly message: string,
        public readonly severity: DiagnosticSeverity,
    ) {}
}

export const TextEdit = {
    replace: (range: Range, newText: string) => ({ range, newText }),
};

export const mockStatusItem = {
    name: undefined as string | undefined,
    text: '',
    detail: undefined as string | undefined,
    severity: LanguageStatusSeverity.Information,
    busy: false,
    command: undefined as { title: string; command: string } | undefined,
    dispose: jest.fn(),
};

export const window = {
    showInformationMessage: jest.fn(),
    showErrorMessage: jest.fn(),
};

export const workspace = {
    getConfiguration: jest.fn(() => ({
        get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
    })),
    onWillSaveTextDocument: jest.fn(() => ({ dispose: jest.fn() })),
    onDidChangeConfiguration: jest.fn(() => ({ dispose: jest.fn() })),
};

export const languages = {
    createDiagnosticCollection: jest.fn(() => ({ set: jest.fn(), dispose: jest.fn() })),
    createLanguageStatusItem: jest.fn(() => mockStatusItem),
    registerDocumentFormattingEditProvider: jest.fn(() => ({ dispose: jest.fn() })),
    registerDocumentRangeFormattingEditProvider: jest.fn(() => ({ dispose: jest.fn() })),
};

export const commands = {
    registerCommand: jest.fn(() => ({ dispose: jest.fn() })),
};
