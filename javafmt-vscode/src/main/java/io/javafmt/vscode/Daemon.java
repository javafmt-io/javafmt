package io.javafmt.vscode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Daemon {

    public static void main(final String[] args) throws IOException {
        final var in = new BufferedReader(new InputStreamReader(System.in, UTF_8));
        final var out = new PrintWriter(new OutputStreamWriter(System.out, UTF_8), true);
        String line;
        while ((line = in.readLine()) != null) {
            final var req = Json.parse(line, FormatRequest.class);
            out.println(Json.serialize(DaemonHandler.handle(req)));
        }
    }

    private Daemon() {}
}
