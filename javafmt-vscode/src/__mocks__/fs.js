const actual = jest.requireActual('fs');
const realExistsSync = actual.existsSync;

module.exports = {
    ...actual,
    existsSync: jest.fn(function existsSync(p) {
        return typeof realExistsSync === 'function' ? realExistsSync(p) : false;
    }),
};
