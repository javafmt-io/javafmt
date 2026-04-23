package io.github.jschneidereit.grind;

public record GrindConfig(boolean reorderMembers, boolean strict) {

    public GrindConfig() {
        this(false, false);
    }

    public GrindConfig(final boolean reorderMembers) {
        this(reorderMembers, false);
    }

    public static GrindConfig defaults() {
        return new GrindConfig();
    }

    public GrindConfig withStrict(final boolean value) {
        return new GrindConfig(reorderMembers, value);
    }
}
