public @interface Marker {

    String value();

    String[] tags();

    String label() default "none";

    int[] codes() default {1, 2,};

    Class<?> target() default Object.class;
}
