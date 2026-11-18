plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The version comes from the newest numbered entry in CHANGELOG.md ("## 1.0.0 (2026-11-17)"),
// so a build can never carry a number the changelog does not explain. There is
// deliberately no command-line override. tools/check.mjs checks that the entry
// names the rule pack and codebook this tree actually holds.
val mleVersion: String = Regex("""^## (\d+\.\d+\.\d+) \(""", RegexOption.MULTILINE)
    .find(rootProject.file("CHANGELOG.md").readText())
    ?.groupValues?.get(1)
    ?: error("CHANGELOG.md has no released version heading like \"## 1.0.0 (2026-11-17)\"")

subprojects {
    group = "com.medialiteracy"
    version = mleVersion

    // `gradlew publishAllPublicationsToStagingRepository` writes a Maven folder
    // to build/engine-release, ready to copy into an app that keeps a packaged copy of the engine.
    plugins.withId("maven-publish") {
        extensions.configure<org.gradle.api.publish.PublishingExtension> {
            repositories {
                maven {
                    name = "staging"
                    url = uri(rootProject.layout.buildDirectory.dir("engine-release"))
                }
            }
        }
    }
}

// Master task to run the full MLE suite in optimal order
tasks.register("suite") {
    group = "tools"
    description = "Run the full MLE suite in optimal order (Haystack -> Gauntlet -> Hammer -> Shield)"
    dependsOn(":tools:feed-harvest:haystack")
    dependsOn(":tools:adversarial-fuzz:gauntlet")
    dependsOn(":tools:rule-eval:hammer")
    dependsOn(":tools:benchmark:shield")
}

gradle.projectsEvaluated {
    val haystack = tasks.findByPath(":tools:feed-harvest:haystack")
    val gauntlet = tasks.findByPath(":tools:adversarial-fuzz:gauntlet")
    val hammer = tasks.findByPath(":tools:rule-eval:hammer")
    val shield = tasks.findByPath(":tools:benchmark:shield")

    // Inside the suite, Haystack reports on live feeds but never writes the
    // intake fixture: new rows are added only by running haystack on its own.
    if (haystack is JavaExec) {
        gradle.taskGraph.whenReady {
            if (hasTask(":suite")) haystack.args("--dry-run")
        }
    }
    if (gauntlet != null && haystack != null) {
        gauntlet.mustRunAfter(haystack)
    }
    if (hammer != null && gauntlet != null) {
        hammer.mustRunAfter(gauntlet)
    }
    if (shield != null && hammer != null) {
        shield.mustRunAfter(hammer)
    }
}

