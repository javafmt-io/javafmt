plugins {
    id("javafmt.java-conventions")
    id("javafmt.publish-conventions")
}

description = "Spotless integration for javafmt."

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.spotless.lib)
    testImplementation(libs.spotless.lib)
}
