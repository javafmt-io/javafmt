package io.javafmt.vscode;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class Daemon {

    public void main(final String[] args) throws IOException {
        serve(new BufferedReader(new InputStreamReader(System.in, UTF_8)), new PrintWriter(new OutputStreamWriter(System.out, UTF_8), true));
    }

    void serve(final BufferedReader in, final PrintWriter out) {
        in.lines()
                .forEach(line -> {
                    try {
                        out.println(Json.serialize(DaemonHandler.handle(Json.parse(line, FormatRequest.class))));
                    } catch (final RuntimeException e) {
                        log.warn("dropped malformed request line", e);
                    }
                });
    }
}
