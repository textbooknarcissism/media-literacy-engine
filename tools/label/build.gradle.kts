plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:flagging"))
}

application {
    mainClass.set("com.medialiteracy.label.MainKt")
}

// Run from the project root so that "fixtures/headlines/..." resolves, and
// keep the real keyboard attached: this tool asks questions and waits for
// answers, which is unusual for a Gradle task and does not work without it.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.register<JavaExec>("anvil") {
    group = "tools"
    description = "Anvil: Run the interactive headline labeling tool"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.label.MainKt")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

