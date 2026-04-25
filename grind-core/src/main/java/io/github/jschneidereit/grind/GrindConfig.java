package io.github.jschneidereit.grind;

/**
 * Formatter configuration.
 *
 * @param reorderMembers when {@code true}, sort class/enum/record body members by visibility group
 *     (per CLAUDE.md ordering). The formatter detects forward references between static-final
 *     field initializers and skips reordering for that container with a {@link Diagnostic.Warning}
 *     when found, but more exotic forward-reference patterns (e.g. through nested-type or
 *     qualified-name access) are not detected and may produce non-compiling output.
 */
public record GrindConfig(boolean reorderMembers) {

    public GrindConfig() {
        this(false);
    }

    public static GrindConfig defaults() {
        return new GrindConfig();
    }
}
