plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:flagging"))
}

application {
    mainClass.set("com.medialiteracy.ruleeval.MainKt")
}

// Run from the project root so that "fixtures/headlines/..." resolves.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.register<JavaExec>("hammer") {
    group = "tools"
    description = "Hammer: Run the rule evaluator and profiler"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.ruleeval.MainKt")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

