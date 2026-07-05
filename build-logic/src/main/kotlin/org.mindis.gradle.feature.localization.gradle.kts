import java.util.Properties

plugins {
    id("java")
}

// Verifies the full-text localization convention (PLAN.md §2.3):
// every Localization.lang("...") literal and FXML "%..." resource key must
// exist as a key in the English bundle. The English bundle is the single
// source of truth; other languages may lag (English text is the fallback).
val verifyLocalization = tasks.register("verifyLocalization") {
    group = "verification"
    description = "Checks that all Localization.lang(...) and FXML % keys exist in the English bundle."

    val sourceDir = layout.projectDirectory.dir("src/main")
    val bundleFile = rootProject.layout.projectDirectory
        .file("mindis-core/src/main/resources/org/mindis/core/l10n/MinDis_en.properties")

    inputs.dir(sourceDir).withPropertyName("sources").optional()
    inputs.file(bundleFile).withPropertyName("englishBundle")
    outputs.upToDateWhen { true }

    doLast {
        val bundle = Properties()
        bundleFile.asFile.reader(Charsets.UTF_8).use { bundle.load(it) }
        val knownKeys = bundle.keys.map { it.toString() }.toSet()

        val langPattern = Regex("""Localization\s*\.\s*lang\(\s*"((?:[^"\\]|\\.)*)"""")
        val fxmlKeyPattern = Regex("""(?:text|title|promptText)="%([^"]+)"""")

        val missing = mutableListOf<String>()
        val srcRoot = sourceDir.asFile
        if (srcRoot.exists()) {
            srcRoot.walkTopDown()
                .filter { it.isFile && (it.extension == "java" || it.extension == "fxml") }
                .forEach { file ->
                    var text = file.readText(Charsets.UTF_8)
                    if (file.extension == "java") {
                        // Strip comments so javadoc examples are not treated as usages.
                        text = text
                            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("""//.*"""), "")
                    }
                    val keys = when (file.extension) {
                        "java" -> langPattern.findAll(text).map { it.groupValues[1].replace("\\\"", "\"") }
                        else -> fxmlKeyPattern.findAll(text).map { it.groupValues[1] }
                    }
                    keys.forEach { key ->
                        if (key !in knownKeys) {
                            missing += "${file.relativeTo(srcRoot)}: \"$key\""
                        }
                    }
                }
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing keys in MinDis_en.properties:\n" + missing.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyLocalization)
}
