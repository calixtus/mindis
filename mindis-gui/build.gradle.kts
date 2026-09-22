import org.gradle.language.jvm.tasks.ProcessResources
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
    // Track up-to-date variable
    inputs.property("version", applicationVersion)

    // Insert in properties
    filesMatching("org/mindis/gui/about/version.properties") {
        expand("version" to applicationVersion)
    }

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
