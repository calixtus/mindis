// Root build: intentionally minimal. All build logic lives in build-logic
// convention plugins (org.mindis.gradle.*), applied per module via settings.gradle.kts.
//
// Exception: OpenRewrite is applied here at the root so a single rewriteRun /
// rewriteDryRun rewrites all sources across every module (this is OpenRewrite's
// documented behavior when applied to the root of a multi-module build).
plugins {
    id("org.mindis.gradle.feature.compile") // for openrewrite (java plugin + source sets)
    id("org.openrewrite.rewrite") version "7.37.0"
}

dependencies {
    rewrite(platform("org.openrewrite.recipe:rewrite-recipe-bom:3.35.0"))
    rewrite("org.openrewrite.recipe:rewrite-static-analysis")
    rewrite("org.openrewrite.recipe:rewrite-logging-frameworks")
    rewrite("org.openrewrite.recipe:rewrite-testing-frameworks")
    rewrite("org.openrewrite.recipe:rewrite-migrate-java")
}

rewrite {
    activeRecipe("org.mindis.config.rewrite.cleanup")
    exclusion(
        "settings.gradle",
        "**/generated/sources/**",
        "**/generated-src/**",
        "**/src/main/resources/**",
        "**/src/test/resources/**",
        "**/module-info.java",
        "**/*.kts",
        "**/*.py",
        "**/*.xml",
        "**/*.yml"
    )
    plainTextMask("**/*.md")
    failOnDryRunResults = true
}
