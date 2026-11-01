class Inventory {
    static final String[] NAMES = {
            "",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ten",
            "eleven", /*or elva*/
            "twelve",
            "thirteen",
            "fourteen",
            "fifteen",
    };

    static final long[] TABLE = {
            0x02AAB17CF7E90C5EL, /*    0 */
            0xAC424B03E243A8ECL, /*    1 */
            0x72CD5BE30DD5FCD3L, /*    2 */
            0x6D019B93F6F97F3AL, /*    3 */
    };

    static final int[] SHORT = {1, /* one */ 2,};

    void record() {
        store("first", /* the name */ "second", /* the alias */ "third");
    }

    void store(final String name, final String alias, final String other) {}

    enum Policy {
        SILENT, /*0*/
        WARN, /*1*/
        FAIL /*2*/
        ;

        boolean quiet() {
            return this == SILENT;
        }
    }

    enum Level { LOW /*0*/, HIGH /*1*/ }
}
