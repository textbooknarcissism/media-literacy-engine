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
    api(project(":core:flagging"))
    api(project(":core:feeds"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("engine") {
            from(components["java"])
            groupId = "com.medialiteracy"
            artifactId = "engine"
            version = project.version.toString()
            pom {
                name.set("Media Literacy Engine")
                description.set("Offline deterministic media literacy and headline analysis engine for news readers and feed apps.")
                url.set("https://github.com/textbooknarcissism/media-literacy-engine")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
        create<MavenPublication>("mediaLiteracyEngine") {
            from(components["java"])
            groupId = "com.medialiteracy"
            artifactId = "media-literacy-engine"
            version = project.version.toString()
            pom {
                name.set("Media Literacy Engine (Alias)")
                description.set("Offline deterministic media literacy and headline analysis engine for news readers and feed apps.")
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
