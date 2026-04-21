plugins {
    id("grind.java-conventions")
    application
}

application {
    mainClass.set("io.github.jschneidereit.grind.cli.Main")
}

dependencies {
    implementation(project(":grind-core"))
    runtimeOnly(libs.slf4j.simple)
}
