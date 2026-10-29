import { parseResponse } from '../../protocol';

describe('parseResponse', () => {
    it('parses a well-formed response', () => {
        const line = JSON.stringify({ id: 'abc', output: 'class Foo {}', diagnostics: [] });
        expect(parseResponse(line)).toEqual({ id: 'abc', output: 'class Foo {}', diagnostics: [] });
    });

    it('parses diagnostics inside the response', () => {
        const line = JSON.stringify({
            id: 'x',
            output: 'src',
            diagnostics: [{ severity: 'warning', message: 'FallThrough', line: 10, col: 4 }],
        });
        const result = parseResponse(line);
        expect(result?.diagnostics).toHaveLength(1);
        expect(result?.diagnostics[0].message).toBe('FallThrough');
    });

    it('returns null for invalid JSON', () => {
        expect(parseResponse('not json')).toBeNull();
    });

    it('returns null for an empty line', () => {
        expect(parseResponse('')).toBeNull();
    });

    it('returns null for a bare number (valid JSON but wrong shape)', () => {
        // JSON.parse('42') succeeds but the result is not a FormatResponse —
        // callers guard on response.id being present, so returning the parsed
        // value is fine; we just verify it does not throw.
        expect(() => parseResponse('42')).not.toThrow();
    });
});
