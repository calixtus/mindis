plugins {
    id("org.openjfx.javafxplugin")
}

// JavaFX ships platform-classified jars without Gradle module metadata; the
// OpenJFX plugin selects the right classifier for the build platform.
// Version comes from the 'javafxVersion' Gradle property (also used by :versions).
javafx {
    version = providers.gradleProperty("javafxVersion").getOrElse("26.0.1")
    modules = listOf("javafx.base", "javafx.graphics", "javafx.controls")
}

// mindis uses those three JavaFX modules and no others. GemsFX, PickerFX and
// ControlsFX drag javafx-swing and javafx-fxml in as POM dependencies, pinned
// to JavaFX 17 - a version skew against the JavaFX above, since nothing else
// asks for those two coordinates. javafx.fxml is required by no module here,
// and GemsFX's 'requires javafx.swing' is patched out in the module convention
// plugin, so neither is needed: drop them instead of aligning their version.
configurations.configureEach {
    exclude(group = "org.openjfx", module = "javafx-swing")
    exclude(group = "org.openjfx", module = "javafx-fxml")
}
