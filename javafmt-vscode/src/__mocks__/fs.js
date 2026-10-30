const actual = jest.requireActual('node:fs');

module.exports = {
    ...actual,
    accessSync: jest.fn((...args) => actual.accessSync(...args)),
    statSync: jest.fn((...args) => actual.statSync(...args)),
};
