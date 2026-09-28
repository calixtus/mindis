package org.mindis.core.update;

import java.util.Locale;
import java.util.Optional;

/// The platforms MinDis publishes packages for. The keys match the jpackage
/// targets in `mindis-gui/build.gradle.kts` and the CI build matrix, so
/// one name identifies a platform from the build through the manifest to this
/// check.
public enum UpdatePlatform {

    WINDOWS("windows"),
    MACOS("macos"),
    LINUX("linux");

    private final String key;

    UpdatePlatform(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /// Name of this platform's manifest file, published as a release asset
    /// next to the packages it describes. One manifest per platform (rather
    /// than one listing all three) because each platform's packages are built
    /// on their own CI runner, which can then publish a complete manifest of
    /// its own without waiting for the others.
    public String manifestFileName() {
        return "latest-" + key + ".json";
    }

    /// The platform this JVM runs on.
    ///
    /// @return empty on anything else (BSD, Solaris, ...): MinDis publishes no
    ///         package for it, so there is nothing to offer.
    public static Optional<UpdatePlatform> current() {
        return of(System.getProperty("os.name", ""));
    }

    /// Exposed for tests; [#current()] is what production calls.
    static Optional<UpdatePlatform> of(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Optional.of(WINDOWS);
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Optional.of(MACOS);
        }
        if (name.contains("linux")) {
            return Optional.of(LINUX);
        }
        return Optional.empty();
    }
}
