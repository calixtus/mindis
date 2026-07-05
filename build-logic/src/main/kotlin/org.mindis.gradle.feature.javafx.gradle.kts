plugins {
    id("org.openjfx.javafxplugin")
}

// JavaFX ships platform-classified jars without Gradle module metadata; the
// OpenJFX plugin selects the right classifier for the build platform.
// Version comes from the 'javafxVersion' Gradle property (also used by :versions).
javafx {
    version = providers.gradleProperty("javafxVersion").getOrElse("26.0.1")
    modules = listOf("javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml")
}
