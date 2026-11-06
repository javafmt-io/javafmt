class Calls {

    void call(final String message, final Object state, final Exception cause) {
        try {
            call(
                    "Could not obtain the local Windows credentials for SSPI here", Thread.State.TERMINATED,
                    /* TODO: Should be authentication error */ cause);
        } catch (final RuntimeException failure) {
            call(message, state, failure);
        }
    }
}
