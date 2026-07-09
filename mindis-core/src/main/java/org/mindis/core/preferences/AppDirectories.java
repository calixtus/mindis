package org.mindis.core.preferences;

import java.nio.file.Path;

/// Resolves the per-user data directory for MinDis (preferences, roster,
/// services, plans). Follows platform conventions.
public final class AppDirectories {

    private static final String APP_NAME = "MinDis";

    private AppDirectories() {
    }

    public static Path userDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, APP_NAME);
            }
            return Path.of(home, "AppData", "Roaming", APP_NAME);
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", APP_NAME);
        }
        String xdgData = System.getenv("XDG_DATA_HOME");
        if (xdgData != null && !xdgData.isBlank()) {
            return Path.of(xdgData, APP_NAME);
        }
        return Path.of(home, ".local", "share", APP_NAME);
    }
}
