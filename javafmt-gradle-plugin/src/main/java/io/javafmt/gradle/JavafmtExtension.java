package io.javafmt.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public interface JavafmtExtension {

    ListProperty<String> getExcludes();

    Property<Boolean> getAnalyze();

    Property<String> getMaxHeapSize();

    ListProperty<String> getJvmArgs();
}
