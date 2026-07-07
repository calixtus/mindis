plugins {
    id("java")
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
