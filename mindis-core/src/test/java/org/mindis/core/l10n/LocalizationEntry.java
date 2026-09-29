package org.mindis.core.l10n;

import java.nio.file.Path;
import java.util.Objects;

/// A localization key found in a source file, remembering where it was found so a
/// failing test can name the file that needs fixing.
///
/// Adapted from JabRef's `org.jabref.logic.l10n.LocalizationEntry`.
final class LocalizationEntry implements Comparable<LocalizationEntry> {

    private final Path path;
    private final String key;

    LocalizationEntry(Path path, String key) {
        this.path = path;
        this.key = key;
    }

    Path getPath() {
        return path;
    }

    String getKey() {
        return key;
    }

    /// Identity is the key alone: the same key used in several files is one entry.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        return Objects.equals(key, ((LocalizationEntry) o).key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "%s (%s)".formatted(key, path);
    }

    @Override
    public int compareTo(LocalizationEntry o) {
        return key.compareTo(o.key);
    }
}
