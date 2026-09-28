package org.mindis.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/// A MinDis version as `major.minor.patch` - the shape jpackage/MSI
/// accepts, and therefore the only shape MinDis releases carry
/// (`gradle.properties`).
///
/// <p>Comparable by precedence, so "is the release newer than what runs here"
/// is one comparison rather than string juggling.
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {

    /// Shown wherever the running version is unknown - a build run straight
    /// from sources or a jar without the generated resource.
    public static final String UNKNOWN_TEXT = "dev";

    private static final String VERSION_RESOURCE = "/org/mindis/core/version.properties";

    /// Parses `major.minor.patch`, tolerating a leading `v` and any
    /// `-suffix` (a `-SNAPSHOT` build sorts as its own release,
    /// which is the safe reading: it never offers itself an update).
    ///
    /// @return empty when `text` is not a version at all - never an
    ///         exception, because both the running version and the manifest's
    ///         are outside this application's control.
    public static Optional<AppVersion> parse(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        int suffix = indexOfSuffix(trimmed);
        if (suffix >= 0) {
            trimmed = trimmed.substring(0, suffix);
        }
        String[] parts = trimmed.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AppVersion(
                    Integer.parseInt(parts[0]),
                    parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int indexOfSuffix(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character != '.' && (character < '0' || character > '9')) {
                return i;
            }
        }
        return -1;
    }

    /// The running application's version, from the resource the build
    /// generates out of `gradle.properties`.
    ///
    /// @return empty for a build without that resource, or with an
    ///         unparsable one - the update check then does nothing at all,
    ///         rather than guessing a version to compare against.
    public static Optional<AppVersion> current() {
        return parse(currentText());
    }

    /// The running version as text, [#UNKNOWN_TEXT] when there is none.
    /// This is what the About screen shows.
    public static String currentText() {
        try (InputStream in = AppVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                return UNKNOWN_TEXT;
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", UNKNOWN_TEXT);
        } catch (IOException e) {
            return UNKNOWN_TEXT;
        }
    }

    public boolean isNewerThan(AppVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
