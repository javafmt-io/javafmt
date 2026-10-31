class Probe {
    @Deprecated private String f;

    @Override
    public String toString() {
        return "";
    }

    /* why */
    @Override
    public int hashCode() {
        return 1;
    }

    @Deprecated
    @SuppressWarnings("unused")
    Probe() {}

    void m(@Nullable final String s) {}
}
