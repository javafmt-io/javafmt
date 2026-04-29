package io.javafmt.doc;

/**
 * Member ordering buckets. Declaration order defines sort order: earlier
 * constants render earlier in the class body. Stable sort preserves source
 * order within a bucket.
 */
public enum MemberGroup {
    NESTED_TYPE_SEALED,
    STATIC_FIELD,
    STATIC_INITIALIZER,
    INSTANCE_FIELD,
    INSTANCE_INITIALIZER,
    CONSTRUCTOR,
    PUBLIC_METHOD,
    PROTECTED_METHOD,
    PACKAGE_METHOD,
    PRIVATE_METHOD,
    STATIC_METHOD,
    NESTED_TYPE_NORMAL,
    UNKNOWN
}
