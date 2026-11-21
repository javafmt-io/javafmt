import { createValidator } from '@bufbuild/protovalidate';
import * as nodeFs from 'node:fs';
import * as path from 'path';
import { Readable } from 'stream';
import { FormatReplySchema, FormatRequestSchema } from '../../gen/daemon_pb';
import { decodeReplies, encodeRequest } from '../../protocol';
import { decodeAll, decodeRequest } from './wire';

const fixtures = path.resolve(__dirname, '../../../build/daemon-fixtures');

function fixture(name: string): string[] {
    return nodeFs.readFileSync(path.join(fixtures, name), 'utf8').split('\n').filter((line) => line !== '');
}

describe('encodeRequest', () => {
    it('writes the length-delimited request the daemon fixture pins', () => {
        expect(Buffer.from(encodeRequest(1, 'class A{}')).toString('hex')).toBe(fixture('requests.hex')[0]);
    });

    it('carries the path and the classpath, an empty classpath included, only when given', () => {
        const analyzed = decodeRequest(encodeRequest(1, 'class A{}', { path: '/repo/A.java', classpath: '' }));
        expect([analyzed.path, analyzed.classpath]).toEqual(['/repo/A.java', '']);
        const parseOnly = decodeRequest(encodeRequest(1, 'class A{}', {}));
        expect([parseOnly.path, parseOnly.classpath]).toEqual([undefined, undefined]);
    });

    it('writes a request that keeps the protocol rules', () => {
        const request = decodeRequest(encodeRequest(1, ''));
        expect(createValidator().validate(FormatRequestSchema, request).kind).toBe('valid');
    });
});

describe('the pinned replies', () => {
    it('keep the protocol rules', async () => {
        const validator = createValidator();
        const kinds: string[] = [];
        for await (const reply of decodeReplies(Readable.from([Buffer.from(fixture('replies.hex').join(''), 'hex')]))) {
            kinds.push(validator.validate(FormatReplySchema, reply).kind);
        }
        expect(kinds).toEqual(['valid', 'valid', 'valid']);
    });
});

describe('decodeReplies', () => {
    it('decodes the pinned replies to their JSON form', async () => {
        const bytes = Buffer.from(fixture('replies.hex').join(''), 'hex');
        await expect(decodeAll([bytes])).resolves.toEqual(fixture('replies.jsonl').map((line) => JSON.parse(line)));
    });

    it('decodes a reply split across chunks', async () => {
        const bytes = Buffer.from(fixture('replies.hex')[1], 'hex');
        await expect(decodeAll([bytes.subarray(0, 5), bytes.subarray(5)])).resolves.toEqual([JSON.parse(fixture('replies.jsonl')[1])]);
    });

    it('decodes a reply whose two-byte length is split after its first byte', async () => {
        const bytes = Buffer.from(fixture('large-reply.hex')[0], 'hex');
        expect(bytes[0] & 0x80).not.toBe(0);
        await expect(decodeAll([bytes.subarray(0, 1), bytes.subarray(1)])).resolves.toEqual([JSON.parse(fixture('large-reply.jsonl')[0])]);
    });

    it('rejects bytes that are not a reply', async () => {
        await expect(decodeAll([Buffer.from([3, 0xff, 0xff, 0xff])])).rejects.toThrow();
    });
});
