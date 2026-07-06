package org.mindis.core.preferences;

import java.nio.file.Path;

/**
 * The resolved per-user data directory, injected into repositories and the
 * preferences service (SOLID/DIP: the environment lookup happens once in
 * {@link DirectoriesFactory}, consumers receive a value).
 */
public record DataDirectory(Path path) {

    public Path resolve(String fileName) {
        return path.resolve(fileName);
    }
}
