import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import groovy.json.JsonSlurper
import org.gradle.language.jvm.tasks.ProcessResources
import java.util.jar.JarFile

// Third-party licensing policy (ADR 008) enforced on the build: every module
// that ships with the application must carry a license from
// config/licenses/allowed-licenses.json, and the notices handed to users are
// generated from the same resolved dependency set - never maintained by hand.
plugins {
    id("java")
    id("com.github.jk1.dependency-license-report")
}

licenseReport {
    // The application project's runtimeClasspath is exactly what jpackage
    // bundles, so it is the set that carries distribution obligations.
    projects = arrayOf(project)
    configurations = arrayOf("runtimeClasspath")
    allowedLicensesFile = rootProject.file("config/licenses/allowed-licenses.json")
    // Maps the many spellings of one license ("Apache 2.0", "The Apache
    // Software License, Version 2.0", ...) onto a canonical name, so the
    // allowlist can be an exact-match list instead of a guess.
    filters = arrayOf(LicenseBundleNormalizer(mapOf("createDefaultTransformationRules" to true)))
    renderers = arrayOf<ReportRenderer>(
            // Full inventory for review, with the license files extracted from
            // the jars next to it; build output, not shipped.
            InventoryMarkdownReportRenderer("dependency-inventory.md", "MinDis"),
            // Machine-readable input for the shipped notices below.
            JsonReportRenderer("licenses.json", false))
}

/// The user-facing notices file: preamble (the app's own license, the bundled
/// Java runtime, the bundled font) followed by every shipped module with its
/// license, followed by the `NOTICE` files those modules carry - which the
/// Apache License 2.0 section 4(d) requires to be passed on.
val generateThirdPartyNotices = tasks.register("generateThirdPartyNotices") {
    description = "Generates THIRD-PARTY-NOTICES.md from the resolved runtime dependencies."
    group = "documentation"
    dependsOn(tasks.named("generateLicenseReport"))

    val licensesJson = layout.buildDirectory.file("reports/dependency-license/licenses.json")
    val preamble = rootProject.layout.projectDirectory.file("config/licenses/NOTICE-preamble.md")
    val target = layout.buildDirectory.file("generated/notices/THIRD-PARTY-NOTICES.md")
    val runtimeJars = configurations.named("runtimeClasspath")

    inputs.file(licensesJson)
    inputs.file(preamble)
    outputs.file(target)

    doLast {
        val report = JsonSlurper().parse(licensesJson.get().asFile) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val dependencies = (report["dependencies"] as List<Map<*, *>>)
                .sortedBy { "${it["moduleName"]}:${it["moduleVersion"]}" }

        val text = StringBuilder(preamble.asFile.readText())
        text.append("\n## Bundled modules\n\n")
        for (dependency in dependencies) {
            @Suppress("UNCHECKED_CAST")
            val licenses = (dependency["moduleLicenses"] as? List<Map<*, *>>).orEmpty()
                    .mapNotNull { it["moduleLicense"] as? String }
                    .distinct()
                    .ifEmpty { listOf("see the module's own distribution") }
            text.append("- **${dependency["moduleName"]} ${dependency["moduleVersion"]}** - ")
                    .append(licenses.joinToString(" / "))
                    .append("\n")
        }

        val notices = collectJarNotices(runtimeJars.get().files)
        if (notices.isNotEmpty()) {
            text.append("\n## NOTICE files of bundled modules\n")
            notices.forEach { (jar, notice) ->
                // Four backticks: NOTICE files are free-form text and some of
                // them contain fenced blocks of their own.
                text.append("\n### $jar\n\n````\n").append(notice.trim()).append("\n````\n")
            }
        }

        target.get().asFile.parentFile.mkdirs()
        target.get().asFile.writeText(text.toString())
    }
}

tasks.named<ProcessResources>("processResources") {
    // Shipped inside the application jar, next to the other About-screen
    // resources, so the notices travel with every installed copy.
    from(generateThirdPartyNotices) {
        into("org/mindis/gui/about")
    }
}

tasks.named("check") {
    dependsOn(tasks.named("checkLicense"))
}

/// Reads `META-INF/NOTICE*` out of every runtime jar, keyed by jar file name.
fun collectJarNotices(jars: Set<File>): Map<String, String> = jars
        .filter { it.name.endsWith(".jar") }
        .sortedBy { it.name }
        .mapNotNull { file ->
            JarFile(file).use { jar ->
                jar.entries().asSequence()
                        .filter { it.name.matches(Regex("META-INF/NOTICE(\\.txt|\\.md)?")) }
                        .firstOrNull()
                        ?.let { entry -> file.name to jar.getInputStream(entry).readBytes().decodeToString() }
            }
        }
        .filter { it.second.isNotBlank() }
        .toMap()
