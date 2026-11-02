class Matrix {
    private final double[][] cells = new double[2][2];

    double[][] copy() {
        final var copy = cells.clone();
        return copy;
    }
}
