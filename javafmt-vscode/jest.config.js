/** @type {import('jest').Config} */
module.exports = {
    preset: 'ts-jest',
    testEnvironment: 'node',
    testMatch: ['**/src/test/unit/**/*.test.ts'],
    moduleNameMapper: {
        '^vscode$': '<rootDir>/src/__mocks__/vscode.ts',
        '^fs$': '<rootDir>/src/__mocks__/fs.js',
        '^child_process$': '<rootDir>/src/__mocks__/child_process.js',
    },
    clearMocks: true,
};
