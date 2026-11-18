plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:flagging"))
    implementation(project(":core:feeds"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

application { mainClass.set("com.medialiteracy.benchmark.MainKt") }

tasks.test { useJUnitPlatform() }

// Run from the project root so that "fixtures/..." and "docs/..." resolve.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("shield") {
    group = "tools"
    description = "Shield: Run the empirical benchmark suite and regression gate"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.benchmark.MainKt")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

