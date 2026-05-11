plugins {
    id("javafmt.java-conventions")
    application
    id("javafmt.native-conventions")
}

application {
    mainClass.set("io.javafmt.cli.Main")
}

nativeBinary {
    imageName.set("javafmt")
}

dependencies {
    implementation(project(":javafmt-core"))
    runtimeOnly(libs.slf4j.simple)
}
