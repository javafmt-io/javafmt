plugins {
    id("javafmt.java-conventions")
    alias(libs.plugins.maven.plugin.development)
    `maven-publish`
    signing
}

description = "Maven plugin for javafmt, the opinionated Java code formatter."

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testCompileOnly(libs.maven.plugin.annotations)
    testRuntimeOnly(libs.slf4j.simple)
}

// maven-plugin-development creates its own MavenPublication; configure POM metadata on all of them.
afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication>().configureEach {
                pom {
                    name.set("javafmt-maven-plugin")
                    description.set(project.description ?: "")
                    url.set("https://javafmt.io")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("jschneidereit")
                            name.set("Jim Schneidereit")
                            email.set("jim@javafmt.io")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/javafmt-io/javafmt.git")
                        developerConnection.set("scm:git:ssh://github.com/javafmt-io/javafmt.git")
                        url.set("https://github.com/javafmt-io/javafmt")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "staging"
                url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
            }
        }
    }

    val signingKey: String? = System.getenv("JRELEASER_GPG_SECRET_KEY")
    val signingPassword: String? = System.getenv("JRELEASER_GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        signing {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(*publishing.publications.toTypedArray())
        }
    }
}
