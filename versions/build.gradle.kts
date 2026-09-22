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
val pdfbox = "3.0.8"
val commonmark = "0.30.0"

dependencies {
    api(platform("org.junit:junit-bom:6.1.3"))
    api(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
}

dependencies.constraints {
    api("org.openjfx:javafx-base:$javafx")
    api("org.openjfx:javafx-graphics:$javafx")
    api("org.openjfx:javafx-controls:$javafx")

    api("com.dlsc.gemsfx:gemsfx:4.3.2")
    api("io.github.mkpaz:atlantafx-base:2.1.0")

    api("io.avaje:avaje-inject:$avajeInject")
    api("io.avaje:avaje-inject-generator:$avajeInject")
    api("jakarta.inject:jakarta.inject-api:2.0.1")

    api("ai.timefold.solver:timefold-solver-core:2.3.0")

    // PDF export. PDFBox and its whole transitive set (fontbox, pdfbox-io,
    // commons-logging) are Apache-2.0, which ADR 008 requires; OpenPDF was
    // MPL-2.0/LGPL-2.1 and is deliberately not used.
    api("org.apache.pdfbox:pdfbox:$pdfbox")
    api("org.apache.pdfbox:fontbox:$pdfbox")
    api("org.apache.pdfbox:pdfbox-io:$pdfbox")

    // Plan export templating: one Pebble template renders Markdown, which is
    // parsed once and drawn by the per-format renderers. Pebble is BSD-3,
    // commonmark BSD-2 - both permissive, see ADR 008.
    api("io.pebbletemplates:pebble:4.1.2")
    api("org.unbescape:unbescape:1.1.6.RELEASE")
    api("org.commonmark:commonmark:$commonmark")
    api("org.commonmark:commonmark-ext-gfm-tables:$commonmark")

    api("org.jspecify:jspecify:1.0.1")

    // mindis's own code logs through slf4j-api (never java.util.logging
    // directly, except org.mindis.core.logging.LoggingBootstrap and
    // org.mindis.gui.logging.AlertOnErrorHandler, which configure/extend the
    // JUL backend itself). slf4j-jdk14 binds both that and every
    // slf4j-emitting third-party library (avaje-inject et al) into the same
    // JUL handlers, so console/file output is unified regardless of caller.
    api("org.slf4j:slf4j-api:2.0.18")
    api("org.slf4j:slf4j-jdk14:2.0.18")

    api("org.kordamp.ikonli:ikonli-core:12.4.0")
    api("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    api("org.kordamp.ikonli:ikonli-materialdesign2-pack:12.4.0")
    // Pinned down from 1.16.x: micrometer-core 1.16.5 class files carry type
    // annotations that crash javac when read from the module path.
    api("io.micrometer:micrometer-core:1.15.12!!")
}
