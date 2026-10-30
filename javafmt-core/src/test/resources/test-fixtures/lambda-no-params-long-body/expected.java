class Fixture {

    Object chain(final Project project, final SourceSet sourceSet) {
        return project.provider(
                () -> sourceSet.getJava()
                        .getSourceDirectories()
                        .getFiles()
                        .stream()
                        .filter(file -> file.exists())
                        .map(file -> file.toPath())
                        .toList());
    }

    Supplier<String> ternary(final boolean condition) {
        return () -> condition
                ? "a very long string literal that goes on and on and on and on and on"
                : "another very long string literal that goes on and on";
    }
}
