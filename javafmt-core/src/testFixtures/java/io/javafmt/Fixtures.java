package io.javafmt;

/** Canonical formatted/unformatted source pairs shared by the integration-module tests. */
public final class Fixtures {

    public static final String UNFORMATTED_FOO = "class Foo { int x; }";

    public static final String FORMATTED_FOO = "class Foo {\n\n    int x;\n}\n";

    public static final String UNFORMATTED_A = "class A { int x; }";

    public static final String FORMATTED_A = "class A {\n\n    int x;\n}\n";

    public static final String STAR_IMPORT_SOURCE = "import java.util.*;\n\nclass Foo {\n\n    List<String> xs;\n}\n";

    public static final String TYPE_WITNESS_SOURCE =
            "import java.util.stream.Stream;\n\nclass Foo {\n\n    Stream<String> xs = Stream.<String>of(\"a\");\n}\n";

    private Fixtures() {}
}
