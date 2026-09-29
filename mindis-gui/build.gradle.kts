import org.gradle.internal.os.OperatingSystem
import org.gradle.language.jvm.tasks.ProcessResources
import org.mindis.gradle.WriteUpdateManifest
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

plugins {
    id("org.mindis.gradle.module")
    id("org.mindis.gradle.feature.javafx")
    id("application")
    id("org.mindis.gradle.feature.packaging")
    id("org.mindis.gradle.check.licenses")
}

application {
    mainModule = "org.mindis.gui"
    mainClass = "org.mindis.gui.MinDisApp"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

// Read at configuration time: reaching for the project from a task action is
// deprecated and fails under the configuration cache.
val applicationVersion = project.version.toString()

tasks.named<ProcessResources>("processResources") {
    // The About screen's Maintainers section reads this at runtime - kept as
    // one file at the repo root (where GitHub expects it) rather than
    // duplicated into a resource, so it's always in sync.
    from(rootProject.file("MAINTAINERS")) {
        into("org/mindis/gui/about")
    }
}

// Installer type: 'msi' (default; needs WiX, present on GitHub runners), 'exe',
// or 'app-image' for a local smoke build without WiX:
//   ./gradlew jpackage -PinstallerType=app-image
// It is set as the target's packageTypes, which is what the packaging plugin
// drives jpackage with - passing '--type' as an extra option instead would run
// *in addition to* the platform default types (windows: exe and msi), i.e.
// jpackage twice into the same destination.
// NOTE: jpackage's WiX v5 'exe' bundler is broken (JDK-8356592) - the msiwrapper
// step fails with AccessDeniedException copying the final .exe. The 'msi' path
// works with WiX v5, so ship an MSI installer.
val installerType = providers.gradleProperty("installerType").getOrElse("msi")

javaModulePackaging {
    applicationName = "MinDis"
    applicationDescription = "MinDis - Minister Dispatcher: altar server planning"
    vendor = "MinDis"

    target("windows") {
        operatingSystem = OperatingSystemFamily.WINDOWS
        architecture = MachineArchitecture.X86_64
        packageTypes = listOf(installerType)
        if (installerType != "app-image") {
            options.addAll("--win-menu", "--win-shortcut", "--win-dir-chooser")
        }
    }
    target("linux") {
        operatingSystem = OperatingSystemFamily.LINUX
        architecture = MachineArchitecture.X86_64
    }
    target("macos") {
        operatingSystem = OperatingSystemFamily.MACOS
        architecture = MachineArchitecture.ARM64
    }
}

dependencies {
    annotationProcessor(platform(project(":versions")))
    annotationProcessor("io.avaje:avaje-inject-generator")

    implementation("org.slf4j:slf4j-jdk14")
}

// PickerFX declares ControlsFX in its published metadata but does not use it:
// its module-info requires only java.base and javafx.controls, and none of its
// 39 classes references org.controlsfx. Dropping it keeps a library mindis
// never calls out of the installer and out of THIRD-PARTY-NOTICES.
configurations.all {
    exclude(group = "org.controlsfx", module = "controlsfx")
}

// The update manifest MinDis's own update check reads
// (docs/adr/010-auto-update.md). jpackage only builds for the host, so this
// describes the host's packages; CI runs it on each platform runner and
// publishes the result as a release asset next to them.
//
//   ./gradlew :gui:jpackage :gui:updateManifest -PreleaseTag=v0.1.0
//
// Defaults: the tag is v<version> and the download base is that tag's release
// page on GitHub - which is where the CI release job attaches the packages.
val hostPlatform = when {
    OperatingSystem.current().isWindows -> "windows"
    OperatingSystem.current().isMacOsX -> "macos"
    else -> "linux"
}
val releaseTag = providers.gradleProperty("releaseTag").getOrElse("v$applicationVersion")
val releaseBaseUrl = providers.gradleProperty("releaseBaseUrl")
    .getOrElse("https://github.com/calixtus/mindis/releases")

tasks.register<WriteUpdateManifest>("updateManifest") {
    group = "distribution"
    description = "Writes latest-<platform>.json (and .sha256 sidecars) for the packages jpackage built."

    packageDirectory = layout.buildDirectory.dir("packages/$hostPlatform")
    platform = hostPlatform
    appVersion = applicationVersion
    downloadBaseUrl = "$releaseBaseUrl/download/$releaseTag/"
    releaseNotesUrl = "$releaseBaseUrl/tag/$releaseTag"
    manifestFile = layout.buildDirectory.file("packages/$hostPlatform/latest-$hostPlatform.json")
}
