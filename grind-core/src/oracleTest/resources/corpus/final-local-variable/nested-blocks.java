class Fixture {
    int reassignedInNestedIf(boolean flag) {
        int value = 1;
        if (flag) {
            value = 2;
        }
        return value;
    }

    int neverReassignedAcrossNesting() {
        int outer = 10;
        if (outer > 0) {
            int inner = outer * 2;
            System.out.println(inner);
        }
        return outer;
    }
}
