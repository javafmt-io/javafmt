class Fixture {
    void loop() {
        outer:
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (i == j) {
                    break outer;
                }
            }
        }
    }
}
