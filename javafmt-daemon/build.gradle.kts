import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("javafmt.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.protobuf)
}

description = "The javafmt --daemon protocol in protobuf, and the server that speaks it."

val protocol = sourceSets.create("protocol")

java {
    registerFeature("protocol") {
        usingSourceSet(protocol)
    }
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
}

tasks.named<JavaCompile>(protocol.compileJavaTaskName) {
    options.release.set(21)
    options.compilerArgs.clear()
    options.errorprone.enabled.set(false)
}

dependencies {
    "protocolApi"(libs.protobuf.java)
    "protocolApi"(libs.protovalidate) {
        isTransitive = false
    }
    api(project(":javafmt-daemon")) {
        capabilities {
            requireFeature("protocol")
        }
    }
    implementation(project(":javafmt-core"))
    implementation(libs.protobuf.java.util)
    implementation(libs.slf4j.api)
    testFixturesImplementation(libs.protovalidate)
}

val protoElements = configurations.consumable("protoElements") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("javafmt-daemon-proto"))
    }
}

val fixtureElements = configurations.consumable("fixtureElements") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("javafmt-daemon-fixtures"))
    }
}

val protoSources = tasks.register<Sync>("protoSources") {
    from(layout.projectDirectory.dir("src/protocol/proto"))
    from(tasks.named("extractIncludeProtocolProto")) {
        include("buf/validate/validate.proto")
    }
    into(layout.buildDirectory.dir("proto-sources"))
}

artifacts {
    add(protoElements.name, layout.buildDirectory.dir("proto-sources")) {
        builtBy(protoSources)
    }
    add(fixtureElements.name, layout.projectDirectory.dir("src/testFixtures/resources/io/javafmt/daemon"))
}
