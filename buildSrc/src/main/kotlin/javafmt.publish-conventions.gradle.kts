plugins {
    `maven-publish`
    signing
}

pluginManager.withPlugin("java") {
    extensions.configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set(project.description ?: providers.gradleProperty("javafmt.description").get())
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
            url = isolated.rootProject.projectDirectory.dir("build/staging-deploy").asFile.toURI()
        }
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/javafmt-io/javafmt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val signingKey: String? = System.getenv("JRELEASER_GPG_SECRET_KEY")
    val signingPassword: String? = System.getenv("JRELEASER_GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
