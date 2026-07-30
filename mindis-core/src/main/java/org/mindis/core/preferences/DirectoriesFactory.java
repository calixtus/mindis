package org.mindis.core.preferences;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

/// Provides the [DataDirectory] bean; the only place that consults the
/// environment for the platform-specific user data path.
@Factory
public final class DirectoriesFactory {

    @Bean
    public DataDirectory dataDirectory() {
        return new DataDirectory(AppDirectories.userDataDir());
    }
}
