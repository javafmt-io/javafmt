import { fromBinary, toJson } from '@bufbuild/protobuf';
import { sizeDelimitedPeek } from '@bufbuild/protobuf/wire';
import { Readable } from 'stream';
import { FormatReplySchema, FormatRequest, FormatRequestSchema } from '../../gen/daemon_pb';
import { decodeReplies } from '../../protocol';

export function decodeRequest(bytes: Uint8Array): FormatRequest {
    const peek = sizeDelimitedPeek(bytes);
    if (peek.eof) {
        throw new Error('no whole request was written');
    }
    return fromBinary(FormatRequestSchema, bytes.subarray(peek.offset, peek.offset + peek.size));
}

export async function decodeAll(chunks: Uint8Array[]): Promise<unknown[]> {
    const replies: unknown[] = [];
    for await (const reply of decodeReplies(Readable.from(chunks))) {
        replies.push(toJson(FormatReplySchema, reply));
    }
    return replies;
}
