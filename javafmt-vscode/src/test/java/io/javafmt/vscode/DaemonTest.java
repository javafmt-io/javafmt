package io.javafmt.vscode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class DaemonTest {

    @Test
    void malformedLineDoesNotKillTheLoop() {
        final var in = new BufferedReader(new StringReader("{not json}\n{\"id\":\"1\",\"source\":\"class A {}\"}\n"));
        final var out = new StringWriter();
        Daemon.serve(in, new PrintWriter(out, true));
        assertThat(out.toString()).contains("\"id\":\"1\"");
    }
}
