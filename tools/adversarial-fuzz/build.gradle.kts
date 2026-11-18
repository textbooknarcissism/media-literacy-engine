plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:flagging"))
}

application {
    mainClass.set("com.medialiteracy.adversarial.MainKt")
}

// Run from the project root so that "fixtures/headlines/..." resolves.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.register<JavaExec>("gauntlet") {
    group = "tools"
    description = "Gauntlet: Run the adversarial stress-test fuzzer"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.adversarial.MainKt")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

