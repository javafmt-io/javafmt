import java.util.List;

class Fixture {

    boolean scan(final List<String> names, final boolean done) {
        var found = false;
        for (List<String> rest = names; rest.size() > 1 /*inlined: rest.isEmpty()*/ && !done; rest = rest.subList(1, rest.size())) {
            found = true;
        }
        for (int count = names.size(); /*
                                        * A single name may pass the limit as long as
                                        * no other name is waiting.
                                        */
                (count > 0)
                    // Not already over the limit
                    && (count < 10); count--) {
            found = !found;
        }
        for (int index = 0; // the first name
                index < names.size(); index++) {
            found = !found;
        }
        return found;
    }
}
