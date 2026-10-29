class Fixture {
    int[] counts = new int[3];

    void accept(final String[] args) {
        System.out.println(args.length + counts.length);
    }
}
