package io.javafmt.intellij;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class VersionGate {

    /**
     * Version probes are cached per binary identity (path, size, and last-modified time), not just
     * per path. A stale cached {@code false} (the binary was old, then upgraded) only costs one
     * unnecessary warning on the next reformat — cheap to accept. A stale cached {@code true} is the
     * direction that actually matters: keyed on path alone, a binary replaced in place at the same
     * path (a downgrade, a symlink swap, a rebuild) would keep reading as supported for the rest of
     * the IDE session, letting a binary that does not understand {@code --daemon} be driven as if
     * it did. Including size and mtime in the key means a replaced binary gets a new key and is
     * re-probed instead of silently reusing the old verdict in either direction.
     */
    private final Map<BinaryIdentity, Boolean> supported = new ConcurrentHashMap<>();

    private final Function<BinaryIdentity, JavafmtBinary.VersionProbe> probe;

    VersionGate(final Function<BinaryIdentity, JavafmtBinary.VersionProbe> probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    Optional<Boolean> isSupported(final BinaryIdentity binary) {
        final var cached = Optional.ofNullable(supported.get(binary));
        if (cached.isPresent()) {
            return cached;
        }
        final var verdict = verdict(probe.apply(binary));
        verdict.ifPresent(answer -> supported.put(binary, answer));
        return verdict;
    }

    private static Optional<Boolean> verdict(final JavafmtBinary.VersionProbe probe) {
        return switch (probe) {
            case JavafmtBinary.VersionProbe.Reported reported -> Optional.of(JavafmtBinary.isSupportedVersion(reported.output()));
            case JavafmtBinary.VersionProbe.Failed ignored -> Optional.of(false);
            case JavafmtBinary.VersionProbe.Inconclusive ignored -> Optional.empty();
        };
    }
}
