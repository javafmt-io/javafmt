package io.javafmt.intellij;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class VersionGate {

    private final Map<BinaryIdentity, Verdict> verdicts = new ConcurrentHashMap<>();

    private final Function<BinaryIdentity, JavafmtBinary.VersionProbe> probe;

    VersionGate(final Function<BinaryIdentity, JavafmtBinary.VersionProbe> probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    Verdict verdict(final BinaryIdentity binary) {
        return Optional.ofNullable(verdicts.get(binary)).orElseGet(() -> probe(binary));
    }

    private Verdict probe(final BinaryIdentity binary) {
        final var verdict = switch (probe.apply(binary)) {
            case JavafmtBinary.VersionProbe.Reported reported ->
                JavafmtBinary.isSupportedVersion(reported.output()) ? Verdict.SUPPORTED : Verdict.TOO_OLD;
            case JavafmtBinary.VersionProbe.Failed _ -> Verdict.TOO_OLD;
            case JavafmtBinary.VersionProbe.Inconclusive _ -> Verdict.UNKNOWN;
        };
        if (verdict != Verdict.UNKNOWN) {
            verdicts.put(binary, verdict);
        }
        return verdict;
    }

    enum Verdict { SUPPORTED, TOO_OLD, UNKNOWN }
}
