// Single source of truth for all dependency versions (JabRef-style versions
// platform, consumed everywhere via jvmDependencyConflicts.consistentResolution).
plugins {
    id("java-platform")
}

javaPlatform {
    allowDependencies()
}

// Kept in sync with the JavaFX version used by org.mindis.gradle.feature.javafx
// (both read the 'javafxVersion' Gradle property).
val javafx = providers.gradleProperty("javafxVersion").getOrElse("26.0.1")

val avajeInject = "12.6"

dependencies {
    api(platform("org.junit:junit-bom:6.1.1"))
}

dependencies.constraints {
    api("org.openjfx:javafx-base:$javafx")
    api("org.openjfx:javafx-graphics:$javafx")
    api("org.openjfx:javafx-controls:$javafx")
    api("org.openjfx:javafx-fxml:$javafx")

    api("com.dlsc.fxmlkit:fxmlkit:1.5.1")
    api("io.github.mkpaz:atlantafx-base:2.1.0")

    api("io.avaje:avaje-inject:$avajeInject")
    api("io.avaje:avaje-inject-generator:$avajeInject")
    api("jakarta.inject:jakarta.inject-api:2.0.1")
}
