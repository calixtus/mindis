import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("org.mindis.gradle.module")
}

dependencies {
    annotationProcessor(platform(project(":versions")))
    annotationProcessor("io.avaje:avaje-inject-generator")
}

// Read at configuration time: reaching for the project from a task action is
// deprecated and fails under the configuration cache.
val applicationVersion = project.version.toString()

// The running version, readable at runtime (org.mindis.core.update.AppVersion):
// the update check compares against it and the About screen shows it. Lives in
// core, not in the GUI, so both read the same one value.
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", applicationVersion)

    filesMatching("org/mindis/core/version.properties") {
        expand("version" to applicationVersion)
    }
}
