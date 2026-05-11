const { EventEmitter } = require('events');
const actual = jest.requireActual('child_process');

function makeMockStream() {
    const e = new EventEmitter();
    e.resume = jest.fn();
    e.pause = jest.fn();
    e.pipe = jest.fn();
    e.write = jest.fn(() => true);
    e.end = jest.fn();
    e.destroy = jest.fn();
    return e;
}

function makeMockProcess() {
    const e = new EventEmitter();
    e.stdin = makeMockStream();
    e.stdout = makeMockStream();
    e.stderr = makeMockStream();
    e.kill = jest.fn();
    e.pid = -1;
    return e;
}

module.exports = {
    ...actual,
    spawn: jest.fn(makeMockProcess),
};
