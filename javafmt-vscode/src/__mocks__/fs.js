const actual = jest.requireActual('node:fs');

module.exports = {
    ...actual,
    accessSync: jest.fn((...args) => actual.accessSync(...args)),
    chmodSync: jest.fn((...args) => actual.chmodSync(...args)),
    statSync: jest.fn((...args) => actual.statSync(...args)),
};
