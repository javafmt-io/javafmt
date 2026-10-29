package io.javafmt.vscode;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
final class Json {

    private final ObjectMapper MAPPER = new ObjectMapper();

    <T> T parse(final String json, final Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    String serialize(final Object obj) {
        return MAPPER.writeValueAsString(obj);
    }
}
