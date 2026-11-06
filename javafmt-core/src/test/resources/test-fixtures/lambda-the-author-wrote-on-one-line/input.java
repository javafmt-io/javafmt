import java.util.List;

class Fixture {
    void run(List<String> xs) {
        xs.forEach(x -> { System.out.println(x); });
        xs.stream().filter(x -> { return !x.isEmpty(); }).map(String::trim).forEach(x -> {System.out.println(x);});
        xs.forEach(x -> { System.out.println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + x); });
        xs.forEach(x -> {
            System.out.println(x);
        });
        xs.forEach(x -> { System.out.println(x); System.out.println(x); });
        Runnable r = () -> { System.out.println(xs); };
        r.run();
    }
}
