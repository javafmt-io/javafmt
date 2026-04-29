class Fixture {
    Runnable wrap(int seed) {
        return new Runnable() {
            @Override
            public void run() {
                helper(seed);
            }

            void helper(int n) {
                System.out.println(n);
            }
        };
    }

    static class Inner {
        int doubleIt(int x) {
            return x * 2;
        }
    }
}
