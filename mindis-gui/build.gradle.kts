plugins {
    id("org.mindis.gradle.module")
    id("org.mindis.gradle.feature.javafx")
    id("application")
}

application {
    mainModule = "org.mindis.gui"
    mainClass = "org.mindis.gui.MinDisApp"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

dependencies {
    annotationProcessor(platform(project(":versions")))
    annotationProcessor("io.avaje:avaje-inject-generator")
}
