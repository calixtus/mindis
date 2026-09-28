package org.mindis.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Writes the update manifest that `org.mindis.core.update.UpdateService` reads.
 * The manifest is what MinDis itself verifies a download against; a `.sha256`
 * sidecar is written only for packages that have none yet (jpackage writes its
 * own for the installers, and those are left exactly as they are).
 *
 * One manifest per platform, listing the packages jpackage produced on *this*
 * runner - so a platform's release assets and the manifest describing them are
 * always written by the same build (docs/adr/010-auto-update.md).
 */
abstract class WriteUpdateManifest : DefaultTask() {

    /** Where jpackage put this platform's packages (`build/packages/<platform>`). */
    @get:InputDirectory
    abstract val packageDirectory: DirectoryProperty

    /** `windows`, `macos` or `linux` - matches `UpdatePlatform.key()`. */
    @get:Input
    abstract val platform: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    /**
     * Where the release's assets can be downloaded, with a trailing slash. The
     * URLs must be pinned to this release: the checksums below are for these
     * exact files, so a "latest" URL would go stale the moment the next release
     * is published.
     */
    @get:Input
    abstract val downloadBaseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseNotesUrl: Property<String>

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun write() {
        val directory = packageDirectory.get().asFile
        val installers = directory.listFiles { file: File ->
            file.isFile && INSTALLER_EXTENSIONS.any { file.name.endsWith(it) }
        }?.sortedBy { it.name }.orEmpty()
        val portables = directory.listFiles { file: File ->
            file.isFile && file.name.contains("-portable-") && file.name.endsWith(".zip")
        }?.sortedBy { it.name }.orEmpty()

        if (installers.isEmpty()) {
            throw GradleException(
                "No installer found in $directory - run :gui:jpackage first " +
                    "(an app-image-only build has nothing to publish)."
            )
        }

        val entries = installers.map { it to "INSTALLER" } + portables.map { it to "PORTABLE" }
        val artifacts = entries.map { (file, kind) ->
            val checksum = sha256(file)
            val sidecar = File(directory, file.name + ".sha256")
            if (!sidecar.exists()) {
                sidecar.writeText("$checksum  ${file.name}\n")
            }
            """
            |    {
            |      "fileName": "${file.name}",
            |      "url": "${downloadBaseUrl.get().trimEnd('/')}/${file.name}",
            |      "sha256": "$checksum",
            |      "size": ${file.length()},
            |      "kind": "$kind"
            |    }
            """.trimMargin()
        }

        val notes = releaseNotesUrl.orNull
        val manifest = buildString {
            appendLine("{")
            appendLine("""  "version": "${appVersion.get()}",""")
            appendLine("""  "platform": "${platform.get()}",""")
            if (!notes.isNullOrBlank()) {
                appendLine("""  "releaseNotesUrl": "$notes",""")
            }
            appendLine("""  "artifacts": [""")
            appendLine(artifacts.joinToString(",\n"))
            appendLine("  ]")
            appendLine("}")
        }
        val target = manifestFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(manifest)
        logger.lifecycle("Wrote {} ({} artifacts)", target, artifacts.size)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** Every package type jpackage produces that can install MinDis. */
        val INSTALLER_EXTENSIONS = listOf(".msi", ".exe", ".deb", ".rpm", ".dmg", ".pkg")
    }
}
