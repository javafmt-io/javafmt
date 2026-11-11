import java.util.List;

class Fixture {
    int[] small = {1, 2,};
    int[][] grid = {
        {1, 0},
        {0, 1}
    };
    String[] wide = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccccccccc", "dddddddddddddddddddd"};
    Object[] none = new Object[] {List.of(),};

    @SuppressWarnings({
            "unchecked",
    })
    void handle() {}

    @SuppressWarnings({"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"})
    void wideAnnotation() {}
}
