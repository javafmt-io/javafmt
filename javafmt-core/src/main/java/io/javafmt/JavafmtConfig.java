package io.javafmt;

/**
 * Formatter configuration.
 *
 * @param reorderMembers when {@code true}, sort class/enum/record body members by visibility group
 *     (per CLAUDE.md ordering). The formatter detects forward references between static-final
 *     field initializers and skips reordering for that container with a {@link Diagnostic.Warning}
 *     when found, but more exotic forward-reference patterns (e.g. through nested-type or
 *     qualified-name access) are not detected and may produce non-compiling output.
 */
public record JavafmtConfig(boolean reorderMembers) implements java.io.Serializable {

    public JavafmtConfig() {
        this(false);
    }

    public static JavafmtConfig defaults() {
        return new JavafmtConfig();
    }
}
