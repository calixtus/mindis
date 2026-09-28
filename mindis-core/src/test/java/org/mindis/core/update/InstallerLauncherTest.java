package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class InstallerLauncherTest {

    private static final Path INSTALLER = Path.of("downloads", "MinDis-0.2.0.msi").toAbsolutePath();

    @Test
    void windowsStartsTheInstallerThroughTheShell() {
        List<String> command = InstallerLauncher.command(UpdatePlatform.WINDOWS, INSTALLER);

        // The empty title argument is what keeps a quoted path from being
        // consumed as start's window title.
        assertEquals(List.of("cmd", "/c", "start", "", INSTALLER.toString()), command);
    }

    @Test
    void macosAndLinuxHandTheFileToTheDesktop() {
        assertEquals(List.of("open", INSTALLER.toString()),
                InstallerLauncher.command(UpdatePlatform.MACOS, INSTALLER));
        assertEquals(List.of("xdg-open", INSTALLER.toString()),
                InstallerLauncher.command(UpdatePlatform.LINUX, INSTALLER));
    }

    @Test
    void everyCommandCarriesAnAbsolutePath() {
        for (UpdatePlatform platform : UpdatePlatform.values()) {
            List<String> command = InstallerLauncher.command(platform, Path.of("MinDis.msi"));
            assertTrue(Path.of(command.getLast()).isAbsolute(),
                    platform + " command is not absolute: " + command);
        }
    }
}
