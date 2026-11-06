package io.javafmt.intellij;

import io.javafmt.daemon.DaemonFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeDaemonTest {

    @Test
    void everyCannedReplyKeepsTheProtocolRules() {
        assertThat(FakeDaemon.CANNED.values())
                .allSatisfy(reply -> assertThat(DaemonFixtures.violations(reply.toBuilder().setId(1).build())).isEmpty());
    }
}
