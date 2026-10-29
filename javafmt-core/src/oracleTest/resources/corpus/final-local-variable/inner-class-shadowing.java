class Fixture {
    Runnable counterFromInnerClass() {
        int count = 100;
        Runnable r = new Runnable() {
            int count = 0;

            @Override
            public void run() {
                count = count + 1;
            }
        };
        return r;
    }
}
