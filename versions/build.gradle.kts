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
    api(platform("com.fasterxml.jackson:jackson-bom:2.22.0"))
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

    api("ai.timefold.solver:timefold-solver-core:2.2.0")
    api("com.github.librepdf:openpdf:3.0.5")

    // Bridges the slf4j-api pulled in transitively (avaje-inject et al) into
    // java.util.logging, so library log calls land in the same console/file
    // handlers as the app's own JUL loggers (org.mindis.core.logging).
    api("org.slf4j:slf4j-jdk14:2.0.18")

    api("org.kordamp.ikonli:ikonli-core:12.4.0")
    api("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    api("org.kordamp.ikonli:ikonli-materialdesign2-pack:12.4.0")
    // Pinned down from 1.16.x: micrometer-core 1.16.5 class files carry type
    // annotations that crash javac when read from the module path.
    api("io.micrometer:micrometer-core:1.15.12!!")
}
