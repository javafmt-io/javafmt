export interface DiagnosticDto {
    severity: 'error' | 'warning';
    message: string;
    line: number;
    col: number;
}

export interface FormatResponse {
    id: string;
    output: string;
    diagnostics: DiagnosticDto[];
}

export function parseResponse(line: string): FormatResponse | null {
    try {
        return JSON.parse(line) as FormatResponse;
    } catch {
        return null;
    }
}
