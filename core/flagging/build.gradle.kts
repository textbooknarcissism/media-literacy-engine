plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

kotlin { jvmToolchain(17) }

java {
    withSourcesJar()
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

tasks.register<Test>("checkRulePack") {
    description = "Runs static lint analysis on rulepack-v1.json to ensure pattern validity and zero dead rules."
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.medialiteracy.flagging.RulePackLintTest")
    }
}

tasks.check {
    dependsOn("checkRulePack")
}

publishing {
    publications {
        create<MavenPublication>("flagging") {
            from(components["java"])
            groupId = "com.medialiteracy"
            artifactId = "flagging"
            version = project.version.toString()
            pom {
                name.set("Media Literacy Flagging")
                description.set("Deterministic rule pack and pattern detection for media literacy.")
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
