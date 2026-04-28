plugins {
    id("grind.java-conventions")
}

dependencies {
    implementation(project(":grind-core"))
    compileOnly(libs.slf4j.api)
}
