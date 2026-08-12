plugins {
    id("java")
    id("org.gradlex.java-module-testing")
}

// Run the tests on the module path, patched into the module under test, rather
// than on the classpath. On the classpath there are no module boundaries at
// all, so a wrong module-info - a `requires` missing from one of the patched
// third-party modules, say - passes every test and then fails in the packaged
// application with an IllegalAccessError.
javaModuleTesting.whitebox(testing.suites.getByName<JvmTestSuite>("test")) {
    requires.add("org.junit.jupiter.api")
    requires.add("org.junit.jupiter.params")
    // ImageIO and java.awt, used by tests that build fixture images.
    requires.add("java.desktop")
}

// Versions come from the :versions platform (junit-bom import).
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Fork tests across processes. See
    // https://docs.gradle.org/current/userguide/performance.html#execute_tests_in_parallel
    maxParallelForks = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    forkEvery = 100
}
