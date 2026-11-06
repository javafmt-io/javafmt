class Fixture {

    int run(final int kind) {
        switch (kind) {
            case 1:
                int first;
                int second;
                first = 1;
                second = 2;
                return first + second;
            default:
                return 0;
        }
    }
}
