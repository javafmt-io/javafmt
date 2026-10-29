module com.example.compact {
    requires java.base;
    requires static java.compiler;

    exports a.b;

    uses a.Service;
}
