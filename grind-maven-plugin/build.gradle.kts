plugins {
    id("grind.java-conventions")
    alias(libs.plugins.maven.plugin.development)
}

dependencies {
    implementation(project(":grind-core"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.plugin.annotations)
}
