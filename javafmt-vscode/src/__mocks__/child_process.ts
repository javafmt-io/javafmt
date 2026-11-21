import type { ChildProcess } from 'child_process';
import { EventEmitter } from 'events';
import { PassThrough } from 'stream';

const actual = jest.requireActual<typeof import('child_process')>('child_process');

export type MockProcess = ChildProcess & { stdin: PassThrough; stdout: PassThrough; stderr: PassThrough };

export function mockProcess(): MockProcess {
    const stdin = new PassThrough();
    jest.spyOn(stdin, 'write');
    return Object.assign(new EventEmitter(), {
        stdin,
        stdout: new PassThrough(),
        stderr: new PassThrough(),
        kill: jest.fn(),
    }) as unknown as MockProcess;
}

export const spawn = jest.fn(mockProcess);

export const { exec, execFile, execFileSync, execSync, fork, spawnSync } = actual;
