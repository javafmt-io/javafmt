enum Kind { FIRST /* one */ /* two */, LAST /* three */ /* four */ }

class Calls {
    void call(final Object first, final Object second) {
        call(first, /* one */ /* two */ second);
    }
}
