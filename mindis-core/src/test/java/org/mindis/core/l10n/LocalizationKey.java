package org.mindis.core.l10n;

/// A localization key as it appears in the sources - the key *is* the English text -
/// together with its escaped form for a `.properties` file.
///
/// Adapted from JabRef's `org.jabref.logic.l10n.LocalizationKey`.
final class LocalizationKey {

    private final String key;
    private final String escapedPropertyKey;

    /// @param key plain key, unescaped, e.g. `Newline follows\nsecond line`
    private LocalizationKey(String key) {
        this.key = key;
        // Space, #, !, = and : are not allowed in properties file keys. # and ! are only
        // disallowed at the beginning of a key, but escaping every instance is easier.
        this.escapedPropertyKey = key
                .replace("\n", "\\n")
                .replace(" ", "\\ ")
                .replace("#", "\\#")
                .replace("!", "\\!")
                .replace("=", "\\=")
                .replace(":", "\\:");
    }

    static LocalizationKey fromKey(String key) {
        return new LocalizationKey(key);
    }

    /// @param key the key as written between the quotes of a Java string literal - `\n`
    ///            (an escaped newline) and `\\` are kept as they are
    static LocalizationKey fromEscapedJavaString(String key) {
        return new LocalizationKey(key);
    }

    String getEscapedPropertiesKey() {
        return escapedPropertyKey;
    }

    String getValueForEnglishPropertiesFile() {
        return key.replace("\n", "\\n");
    }

    String getKey() {
        return key;
    }
}
