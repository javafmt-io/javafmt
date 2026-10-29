import java.util.List;

class Fixture {

    List<@Marker(firstAttributeName = "alpha value here now", secondAttributeName = "beta value here now", thirdAttributeName = "gamma val") String> values;
}

@interface Marker {

    String firstAttributeName();

    String secondAttributeName();

    String thirdAttributeName();
}
