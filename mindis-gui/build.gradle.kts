import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

plugins {
    id("org.mindis.gradle.module")
    id("org.mindis.gradle.feature.javafx")
    id("application")
    id("org.mindis.gradle.feature.packaging")
}

application {
    mainModule = "org.mindis.gui"
    mainClass = "org.mindis.gui.MinDisApp"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

// Bakes the project version into a resource read by the About module -
// avoids relying on JPMS module/jar-manifest versioning, which jpackage's
// jlink runtime image does not carry through reliably.
tasks.named<ProcessResources>("processResources") {
    filesMatching("org/mindis/gui/about/version.properties") {
        expand("version" to project.version)
    }
}

// Installer type: 'exe' (default; needs WiX, present on GitHub runners) or
// 'app-image' for a local smoke build without WiX:
//   rm -rf mindis-gui/build/packages && ./gradlew jpackage -PinstallerType=app-image
// (delete the packages dir first - jpackage refuses an existing app-image dir)
// 'msi' (default), 'exe', or 'app-image' (local smoke build, no WiX).
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
        if (installerType == "app-image") {
            // Local smoke build without WiX: stop after the app-image step.
            singleStepPackaging = true
            options.addAll("--type", "app-image")
        } else {
            options.addAll("--type", installerType,
                    "--win-menu", "--win-shortcut", "--win-dir-chooser")
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
