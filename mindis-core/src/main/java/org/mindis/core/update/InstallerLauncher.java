package org.mindis.core.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Hands a downloaded installer to the operating system and lets it do the
/// installing (docs/adr/010-auto-update.md): MinDis ships jpackage packages,
/// whose contents are signed as a unit, so the update is a package install -
/// never a file swap inside the running application.
///
/// <p>The launched process outlives this one on purpose; MinDis quits right
/// after, because the installer cannot replace files that are still in use.
public final class InstallerLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstallerLauncher.class);

    private InstallerLauncher() {
    }

    /// The command that opens `installer` in the platform's own installer
    /// UI. Pure, so the per-platform decision is testable
    /// (`InstallerLauncherTest`).
    ///
    /// <p>Windows: `cmd /c start` runs the MSI/EXE through the shell, so
    /// UAC elevation is the installer's own prompt rather than something MinDis
    /// has to arrange. macOS and Linux: `open`/`xdg-open` hand the
    /// `.dmg`/`.deb`/`.rpm` to the desktop's package or disk-image
    /// handler. Nothing is installed silently - the user sees, and confirms,
    /// the real installer.
    public static List<String> command(UpdatePlatform platform, Path installer) {
        String file = installer.toAbsolutePath().toString();
        return switch (platform) {
            // The empty "" is start's window-title argument: without it, a
            // quoted path would be taken *as* the title and nothing would run.
            case WINDOWS -> List.of("cmd", "/c", "start", "", file);
            case MACOS -> List.of("open", file);
            case LINUX -> List.of("xdg-open", file);
        };
    }

    /// Starts the installer and returns as soon as it is running.
    ///
    /// @throws IOException when the process cannot be started at all (no
    ///         `xdg-open` on a bare Linux desktop, for instance) - the
    ///         caller then still has the downloaded file to point the user at.
    public static void launch(UpdatePlatform platform, Path installer) throws IOException {
        List<String> command = command(platform, installer);
        LOGGER.info("Starting update installer: {}", command);
        // No working directory of its own: the command carries an absolute
        // path, and the download directory (which would be the obvious choice)
        // is a temporary one the installer must not be pinned to.
        new ProcessBuilder(command).inheritIO().start();
    }
}
