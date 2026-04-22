package io.github.jschneidereit.grind;

public record GrindConfig(boolean reorderMembers) {

    public GrindConfig() {
        this(false);
    }

    public static GrindConfig defaults() {
        return new GrindConfig();
    }
}
