import { create } from '@bufbuild/protobuf';
import { sizeDelimitedDecodeStream, sizeDelimitedEncode } from '@bufbuild/protobuf/wire';
import { FormatReplySchema, FormatRequestSchema } from './gen/daemon_pb';
import type { FormatReply } from './gen/daemon_pb';

export type { Diagnostic, FormatReply } from './gen/daemon_pb';
export { Kind, Severity } from './gen/daemon_pb';

export type FileScope = { path?: string; classpath?: string };

export function encodeRequest(id: number, source: string, scope: FileScope = {}): Uint8Array {
    return sizeDelimitedEncode(FormatRequestSchema, create(FormatRequestSchema, { id, source, ...scope }));
}

export function decodeReplies(stream: AsyncIterable<Uint8Array>): AsyncIterable<FormatReply> {
    return sizeDelimitedDecodeStream(FormatReplySchema, stream);
}
