plugins {
    id("java")
    id("org.gradlex.java-module-dependencies")
    id("org.gradlex.jvm-dependency-conflict-resolution")
    id("org.gradlex.extra-java-module-info")
    id("org.mindis.gradle.feature.compile")
    id("org.mindis.gradle.feature.test")
    id("org.mindis.gradle.feature.localization")
    id("org.mindis.gradle.check.checkstyle")
    id("org.mindis.gradle.check.javadoc")
    id("org.mindis.gradle.check.modernizer")
    id("org.mindis.gradle.check.nullaway")
}

// All dependency versions come from the :versions platform (JabRef pattern).
jvmDependencyConflicts {
    consistentResolution {
        platform(":versions")
    }
}

// jlink (used by jpackage) rejects automatic modules; patch the few
// non-modular jars into proper modules. Module names must match the
// Automatic-Module-Name they had before (other module-infos require them).
extraJavaModuleInfo {
    failOnAutomaticModules = false

    // Annotation processors (incl. errorprone/NullAway and their transitive
    // deps, e.g. com.github.kevinstern:software-and-algorithms, which ships
    // neither a module-info nor an Automatic-Module-Name) run on a plain
    // -processorpath, never the module path, so they need no module info.
    deactivate(configurations.named("annotationProcessor"))
    deactivate(configurations.named("testAnnotationProcessor"))

    // GemsFX is a real module that declares 'requires javafx.swing', used by
    // exactly one of its classes (SVGUtil, behind SVGImageView) which mindis
    // never touches. Rewriting its descriptor without that requires keeps
    // javafx.swing - a Swing/JavaFX interop bridge - out of the module graph
    // and out of the runtime image. Everything else is carried over verbatim
    // from the published module-info; re-check it when upgrading GemsFX.
    module("com.dlsc.gemsfx:gemsfx", "com.dlsc.gemsfx") {
        patchRealModule()
        exports("com.dlsc.gemsfx")
        exports("com.dlsc.gemsfx.binding")
        exports("com.dlsc.gemsfx.daterange")
        exports("com.dlsc.gemsfx.gridtable")
        exports("com.dlsc.gemsfx.infocenter")
        exports("com.dlsc.gemsfx.paging")
        exports("com.dlsc.gemsfx.skins")
        exports("com.dlsc.gemsfx.treeview")
        exports("com.dlsc.gemsfx.treeview.link")
        exports("com.dlsc.gemsfx.util")
        requires("com.dlsc.pickerfx")
        requires("com.github.weisj.jsvg")
        requires("java.desktop")
        requires("java.logging")
        requires("java.prefs")
        requires("javafx.base")
        requires("javafx.controls")
        requires("javafx.graphics")
        requires("net.synedra.validatorfx")
        requires("org.kordamp.ikonli.bootstrapicons")
        requires("org.kordamp.ikonli.javafx")
        requires("org.kordamp.ikonli.material")
        requires("org.kordamp.ikonli.materialdesign")
    }

    // PDFBox ships Automatic-Module-Names only; commons-logging below it is
    // already a proper multi-release module and needs no patch. The platform
    // modules each one reads come from `jdeps --list-deps` on the jars, not
    // from guessing: a missing one only shows up as an IllegalAccessError at
    // runtime, since tests run on the classpath where there are no module
    // boundaries. Re-run it when upgrading PDFBox.
    module("org.apache.pdfbox:pdfbox", "org.apache.pdfbox") {
        exportAllPackages()
        requires("org.apache.fontbox")
        requires("org.apache.pdfbox.io")
        requires("org.apache.commons.logging")
        requires("java.desktop")
        requires("java.logging")
        requires("java.xml")
        // Signature support is an optional Bouncy Castle feature that mindis
        // does not use and does not put on the module path.
        ignoreServiceProvider("org.bouncycastle.jce.provider.BouncyCastleProvider")
    }
    module("org.apache.pdfbox:fontbox", "org.apache.fontbox") {
        exportAllPackages()
        requires("org.apache.pdfbox.io")
        requires("org.apache.commons.logging")
        // java.awt.geom, for the glyph outlines of an embedded TrueType font.
        requires("java.desktop")
        requires("java.logging")
    }
    module("org.apache.pdfbox:pdfbox-io", "org.apache.pdfbox.io") {
        exportAllPackages()
        requires("org.apache.commons.logging")
        requires("java.logging")
    }
    module("io.micrometer:micrometer-commons", "micrometer.commons") {
        exportAllPackages()
        requires("java.logging")
    }
    module("io.micrometer:micrometer-observation", "micrometer.observation") {
        exportAllPackages()
        requires("micrometer.commons")
        // Service from the optional context-propagation library that is not
        // on the module path.
        ignoreServiceProvider("io.micrometer.context.ThreadLocalAccessor")
    }
    module("io.micrometer:micrometer-core", "micrometer.core") {
        exportAllPackages()
        requires("micrometer.commons")
        requires("micrometer.observation")
        requires("org.HdrHistogram")
        requires("java.logging")
        requires("java.management")
    }
    module("org.hdrhistogram:HdrHistogram", "org.HdrHistogram") {
        exportAllPackages()
    }
    module("org.latencyutils:LatencyUtils", "org.LatencyUtils") {
        exportAllPackages()
        requires("org.HdrHistogram")
    }
    module("javax.inject:javax.inject", "javax.inject") {
        exportAllPackages()
    }
    module("commons-collections:commons-collections", "commons.collections") {
        exportAllPackages()
    }
    module("commons-beanutils:commons-beanutils", "commons.beanutils") {
        exportAllPackages()
        requires("commons.collections")
        requires("java.logging")
        requires("java.desktop")
    }
    module("commons-digester:commons-digester", "commons.digester") {
        exportAllPackages()
        requires("commons.beanutils")
        requires("commons.collections")
        requires("java.logging")
    }
}
