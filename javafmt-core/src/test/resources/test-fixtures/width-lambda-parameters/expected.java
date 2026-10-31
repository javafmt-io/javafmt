class Fixture {
    Object lambda() {
        return (
                argumentAlphaLongName,
                argumentBetaLongName,
                argumentGammaLongName,
                argumentDeltaLongName,
                argumentEpsilonLongName,
                argumentZetaLongName) -> {
            use(argumentAlphaLongName);
            use(argumentBetaLongName);
            use(argumentGammaLongName);
            use(argumentDeltaLongName);
            use(argumentEpsilonLongName);
            return compute(argumentZetaLongName);
        };
    }
}
