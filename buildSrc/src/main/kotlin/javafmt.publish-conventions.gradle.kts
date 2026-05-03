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
                description.set(project.description ?: project.rootProject.description ?: "")
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
            url = project.rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
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
