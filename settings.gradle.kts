pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mindis"

// Project name + group must equal the JPMS module name (org.mindis.<name>) so
// that org.gradlex.java-module-dependencies resolves requires between projects.
include("core", "gui", "workbench", "versions", "native-spike")
project(":core").projectDir = file("mindis-core")
project(":gui").projectDir = file("mindis-gui")
project(":workbench").projectDir = file("mindis-workbench")
