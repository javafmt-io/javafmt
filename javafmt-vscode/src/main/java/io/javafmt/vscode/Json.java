package io.javafmt.vscode;

import tools.jackson.databind.ObjectMapper;

final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static <T> T parse(final String json, final Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    static String serialize(final Object obj) {
        return MAPPER.writeValueAsString(obj);
    }

    private Json() {}
}
