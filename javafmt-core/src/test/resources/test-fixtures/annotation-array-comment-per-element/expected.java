@SuppressWarnings({
        "unchecked", // first reason
        "rawtypes", // second reason
})
class Ledger {

    @SuppressWarnings({
            "unchecked", // the cast is checked by the caller
            "rawtypes", // the legacy registry is raw
    })
    void settle() {}

    @SuppressWarnings({
            // every warning below is reviewed
            "unchecked",
            "rawtypes",
            // nothing else is suppressed
    })
    void close() {}
}
