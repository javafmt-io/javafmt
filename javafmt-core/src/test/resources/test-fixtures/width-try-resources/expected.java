class Fixture {

    void resources() throws Exception {
        try (
                var firstResourceWithAVeryMuchLongerName = open("a");
                var secondResourceWithAVeryMuchLongerName = open("b");
                var thirdResource = open("c")) {
            use();
        }
    }
}
