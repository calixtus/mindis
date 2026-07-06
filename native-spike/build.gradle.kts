// M7 spike: headless solver benchmark, compiled to a native image on CI
// (Windows runner provides MSVC; GraalVM via setup-graalvm action).
// Deliberately NOT a JPMS module and NOT part of the app: classpath mode
// keeps the native-image setup minimal. See PLAN.md M7.
plugins {
    id("java")
    id("application")
    id("org.mindis.gradle.feature.nativeimage")
}

group = "org.mindis"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "org.mindis.spike.SolverBenchmark"
}

dependencies {
    implementation(platform(project(":versions")))
    implementation(project(":core"))
    implementation("ai.timefold.solver:timefold-solver-core")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "mindis-solver-benchmark"
            // Pull known reachability metadata for third-party libraries.
            fallback = false
        }
    }
    metadataRepository {
        enabled = true
    }
}
