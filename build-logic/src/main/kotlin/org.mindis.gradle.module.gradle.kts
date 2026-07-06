plugins {
    id("java")
    id("org.gradlex.java-module-dependencies")
    id("org.gradlex.jvm-dependency-conflict-resolution")
    id("org.gradlex.extra-java-module-info")
    id("org.mindis.gradle.feature.compile")
    id("org.mindis.gradle.feature.test")
    id("org.mindis.gradle.feature.localization")
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

    module("com.github.librepdf:openpdf", "com.github.librepdf.openpdf") {
        exportAllPackages()
        requires("java.desktop")
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
}
