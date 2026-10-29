class Fixture {
    void run(int x) {
        switch (x) {
            case 1 -> System.out.println("one");
            default -> System.out.println("other");
        }
    }
}
