plugins {
    id("javafmt.java-conventions")
    application
}

application {
    mainClass.set("io.javafmt.cli.Main")
}

dependencies {
    implementation(project(":javafmt-core"))
    runtimeOnly(libs.slf4j.simple)
}
