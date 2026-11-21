package io.javafmt.cli;

import lombok.experimental.UtilityClass;

@UtilityClass
final class ExitCode {

    final int OK = 0;

    final int FAILED = 1;

    final int ERROR = 2;
}
