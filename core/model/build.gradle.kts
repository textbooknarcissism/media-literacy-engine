plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin { jvmToolchain(17) }

java {
    withSourcesJar()
}

// Deliberately has no dependencies at all. These are plain data shapes.

publishing {
    publications {
        create<MavenPublication>("model") {
            from(components["java"])
            groupId = "com.medialiteracy"
            artifactId = "model"
            version = project.version.toString()
            pom {
                name.set("Media Literacy Model")
                description.set("Core domain models and categories for media literacy headline analysis.")
                url.set("https://github.com/textbooknarcissism/media-literacy-engine")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
