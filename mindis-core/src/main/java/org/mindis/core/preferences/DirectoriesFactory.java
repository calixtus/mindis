package org.mindis.core.preferences;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

/**
 * Provides the {@link DataDirectory} bean; the only place that consults the
 * environment for the platform-specific user data path.
 */
@Factory
public class DirectoriesFactory {

    @Bean
    public DataDirectory dataDirectory() {
        return new DataDirectory(AppDirectories.userDataDir());
    }
}
