import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

// NullAway static null-checking (JabRef pattern, jablib/build.gradle.kts):
// warn-only for now, so a nullability slip is visible in the build log but
// never breaks the build - packages are being annotated incrementally
// (org.mindis.core.model, org.mindis.core.preferences, ... see JSpecify
// @NullMarked/@Nullable usage). Un-annotated packages are simply not
// checked, so this is safe to apply everywhere from the start.
plugins {
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.7")
}

tasks.withType<JavaCompile>().configureEach {
    // Lets net.ltgt.errorprone add the --add-exports/--add-opens javac needs
    // to run as an annotation processor on JDK 16+, without us doing it by hand.
    options.isFork = true

    options.errorprone {
        disableAllChecks = true
        enable("NullAway")
    }

    options.errorprone.nullaway {
        warn()
        annotatedPackages.add("org.mindis")
    }
}
