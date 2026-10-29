/**
 * Documents the module.
 */
module com.example.commented {
    // why java.base is spelled out
    requires java.base;

    /* block comment on a directive */
    exports com.example.api;

    uses com.example.api.Service;
}
