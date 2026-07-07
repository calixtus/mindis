plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.gradlex:java-module-dependencies:1.13.1")
    implementation("org.gradlex:jvm-dependency-conflict-resolution:2.5")
    implementation("org.gradlex:extra-java-module-info:1.14.2")
    implementation("org.gradlex:java-module-packaging:1.2.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:1.1.3")
    implementation("org.openjfx:javafx-plugin:0.1.0")
    implementation("com.github.andygoossens.modernizer:com.github.andygoossens.modernizer.gradle.plugin:1.14.0")
}
