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

// Installer type: 'exe' (default; needs WiX, present on GitHub runners) or
// 'app-image' for a local smoke build without WiX:
//   rm -rf mindis-gui/build/packages && ./gradlew jpackage -PinstallerType=app-image
// (delete the packages dir first - jpackage refuses an existing app-image dir)
val installerType = providers.gradleProperty("installerType").getOrElse("exe")

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
