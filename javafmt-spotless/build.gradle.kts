plugins {
    id("javafmt.java-conventions")
}

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.slf4j.api)
}
