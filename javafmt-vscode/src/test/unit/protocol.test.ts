import { fromBinary, toJson } from '@bufbuild/protobuf';
import { sizeDelimitedPeek } from '@bufbuild/protobuf/wire';
import { createValidator } from '@bufbuild/protovalidate';
import * as nodeFs from 'node:fs';
import * as path from 'path';
import { Readable } from 'stream';
import { FormatReplySchema, FormatRequestSchema } from '../../gen/daemon_pb';
import { decodeReplies, encodeRequest } from '../../protocol';

const fixtures = path.resolve(__dirname, '../../../build/daemon-fixtures');

function fixture(name: string): string[] {
    return nodeFs.readFileSync(path.join(fixtures, name), 'utf8').split('\n').filter((line) => line !== '');
}

async function decodeAll(bytes: Buffer): Promise<unknown[]> {
    const replies: unknown[] = [];
    for await (const reply of decodeReplies(Readable.from([bytes]))) {
        replies.push(toJson(FormatReplySchema, reply));
    }
    return replies;
}

describe('encodeRequest', () => {
    it('writes the length-delimited request the daemon fixture pins', () => {
        expect(Buffer.from(encodeRequest(1, 'class A{}')).toString('hex')).toBe(fixture('requests.hex')[0]);
    });

    it('writes a request that keeps the protocol rules', () => {
        const bytes = encodeRequest(1, '');
        const peek = sizeDelimitedPeek(bytes);
        if (peek.eof) {
            throw new Error('no whole request was written');
        }
        const request = fromBinary(FormatRequestSchema, bytes.subarray(peek.offset, peek.offset + peek.size));
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
        await expect(decodeAll(bytes)).resolves.toEqual(fixture('replies.jsonl').map((line) => JSON.parse(line)));
    });

    it('decodes a reply split across chunks', async () => {
        const bytes = Buffer.from(fixture('replies.hex')[1], 'hex');
        const replies: unknown[] = [];
        for await (const reply of decodeReplies(Readable.from([bytes.subarray(0, 5), bytes.subarray(5)]))) {
            replies.push(toJson(FormatReplySchema, reply));
        }
        expect(replies).toEqual([JSON.parse(fixture('replies.jsonl')[1])]);
    });

    it('decodes a reply whose two-byte length is split after its first byte', async () => {
        const bytes = Buffer.from(fixture('large-reply.hex')[0], 'hex');
        expect(bytes[0] & 0x80).not.toBe(0);
        const replies: unknown[] = [];
        for await (const reply of decodeReplies(Readable.from([bytes.subarray(0, 1), bytes.subarray(1)]))) {
            replies.push(toJson(FormatReplySchema, reply));
        }
        expect(replies).toEqual([JSON.parse(fixture('large-reply.jsonl')[0])]);
    });

    it('rejects bytes that are not a reply', async () => {
        await expect(decodeAll(Buffer.from([3, 0xff, 0xff, 0xff]))).rejects.toThrow();
    });
});
