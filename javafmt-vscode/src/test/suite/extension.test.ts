import * as assert from 'assert';
import * as vscode from 'vscode';

// Integration tests run inside the Extension Development Host — no mocks needed,
// vscode is the real API. These verify user-visible behaviour end-to-end.

suite('Extension Integration', () => {
    test('VS Code API is accessible in the Extension Development Host', () => {
        assert.strictEqual(typeof vscode.version, 'string');
    });

    test('javafmt.restartDaemon command is contributed', async () => {
        // publisher.name from package.json → "javafmt.javafmt"
        const ext = vscode.extensions.getExtension('javafmt.javafmt');
        assert.ok(ext, 'extension should be loaded in the Extension Development Host');

        if (!ext.isActive) {
            await ext.activate();
        }

        const commands = await vscode.commands.getCommands(true);
        assert.ok(
            commands.includes('javafmt.restartDaemon'),
            'expected javafmt.restartDaemon to be registered after activation',
        );
    });
});
