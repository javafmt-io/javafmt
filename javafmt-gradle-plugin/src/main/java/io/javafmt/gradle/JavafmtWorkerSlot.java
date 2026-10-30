package io.javafmt.gradle;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public abstract class JavafmtWorkerSlot implements BuildService<BuildServiceParameters.None> {

    static final String NAME = "javafmtWorkerSlot";
}
