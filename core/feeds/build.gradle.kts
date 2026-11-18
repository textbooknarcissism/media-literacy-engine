plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin { jvmToolchain(17) }

java {
    withSourcesJar()
}

dependencies {
    api(project(":core:model"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("feeds") {
            from(components["java"])
            groupId = "com.medialiteracy"
            artifactId = "feeds"
            version = project.version.toString()
            pom {
                name.set("Media Literacy Feeds")
                description.set("Story clustering, relevance scoring, and framing comparison for media literacy readers.")
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
