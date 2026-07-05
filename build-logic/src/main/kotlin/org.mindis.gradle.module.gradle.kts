plugins {
    id("java")
    id("org.gradlex.java-module-dependencies")
    id("org.gradlex.jvm-dependency-conflict-resolution")
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
