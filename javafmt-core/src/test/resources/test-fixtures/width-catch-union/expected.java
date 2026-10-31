class Fixture {
    void run() {
        try {
            System.out.println("x");
        } catch (final RuntimeException
                | ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | java.io.IOException ex) {
            System.out.println(ex);
        }
    }
}
