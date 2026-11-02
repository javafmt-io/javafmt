class Matrix {
    private final double[][] cells = new double[2][2];

    double[][] copy() {
        double[][]copy = cells.clone();
        return copy;
    }
}
