pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "media-literacy-engine"

include(":core:model")
include(":core:flagging")
include(":core:feeds")
include(":engine")

// Every folder under tools/ that has a build file is a module.
rootDir.resolve("tools").listFiles()
    ?.filter { it.resolve("build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    ?.forEach { include(":tools:${it.name}") }

