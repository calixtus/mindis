plugins {
    id("org.mindis.gradle.module")
}

dependencies {
    annotationProcessor(platform(project(":versions")))
    annotationProcessor("io.avaje:avaje-inject-generator")
}
