package io.javafmt;

import java.io.Serial;

public final class GitRatchetException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GitRatchetException(final String message) {
        super(message);
    }

    public GitRatchetException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
