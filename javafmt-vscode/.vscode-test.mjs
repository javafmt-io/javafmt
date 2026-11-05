import { defineConfig } from '@vscode/test-cli';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const userDataDir = mkdtempSync(join(process.platform === 'win32' ? tmpdir() : '/tmp', 'javafmt-vscode-'));
process.on('exit', () => rmSync(userDataDir, { recursive: true, force: true }));

export default defineConfig({
    files: 'out/test/suite/**/*.test.js',
    workspaceFolder: '.',
    launchArgs: ['--user-data-dir', userDataDir],
});
