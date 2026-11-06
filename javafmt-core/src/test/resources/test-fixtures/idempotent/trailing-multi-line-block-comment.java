class Fixture {

    int insertRemove; /* if >0, flags for inserting LRM/RLM before/after run,
                         if <0, count of bidi controls within run */
    volatile boolean disableKeepAlive; /* keep-alive has been disabled for this
                                          connection */

    void run(final java.util.BitSet bitSet) {
        bitSet.set(' '); /* encoding a space to a + is done
                          * in the encode() method */
        bitSet.set('-'); /* continuation lines left of the opener
        stay at the block indent */
    }
}
