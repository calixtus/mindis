package org.mindis.core.l10n;

import java.nio.file.Path;

/// Where the localization bundles live, in the two forms the tests need: as a module
/// resource (what [java.util.ResourceBundle] loads) and as a path in the working tree
/// (so a test can list which language files exist at all).
final class LocalizationFiles {

    /// Base name of the bundle, as [Localization] loads it.
    static final String BUNDLE_NAME = "MinDis";

    /// Resource directory of the bundles, absolute so it does not depend on the caller's package.
    static final String RESOURCE_DIRECTORY = "/org/mindis/core/l10n/";

    static final String ENGLISH_BUNDLE_RESOURCE = resource("en");

    /// The bundles as files. The `:core` test task runs with `mindis-core` as its working
    /// directory, so this resolves without knowing the repository root.
    static final Path RESOURCE_SOURCE_DIRECTORY =
            Path.of("src", "main", "resources", "org", "mindis", "core", "l10n");

    private LocalizationFiles() {
    }

    /// @param languageTag a BCP-47 tag, e.g. `de`
    static String resource(String languageTag) {
        return "%s%s_%s.properties".formatted(RESOURCE_DIRECTORY, BUNDLE_NAME, languageTag);
    }
}
