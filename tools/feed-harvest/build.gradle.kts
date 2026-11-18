plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:feeds"))
    implementation(project(":core:flagging"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

application { mainClass.set("com.medialiteracy.harvest.MainKt") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("haystack") {
    group = "tools"
    description = "Haystack: Run the feed harvester"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.harvest.MainKt")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}


tasks.register<JavaExec>("runBroadsheetAudit") {
    group = "application"
    description = "Audits live broadsheet feeds against MLE rules."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.harvest.BroadsheetAuditKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runOutletStudy") {
    group = "application"
    description = "Offline study of outlets, beats and lean over the labeled sets (research only)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.medialiteracy.harvest.OutletStudyKt")
    workingDir = rootProject.projectDir
}
