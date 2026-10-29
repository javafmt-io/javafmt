package io.javafmt;

/** Canonical formatted/unformatted source pairs shared by the integration-module tests. */
public final class Fixtures {

    public static final String UNFORMATTED_FOO = "class Foo { int x; }";

    public static final String FORMATTED_FOO = "class Foo {\n\n    int x;\n}\n";

    public static final String UNFORMATTED_A = "class A { int x; }";

    public static final String FORMATTED_A = "class A {\n\n    int x;\n}\n";

    private Fixtures() {}
}
