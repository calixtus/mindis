package org.mindis.core.preferences;

import java.util.Locale;

/**
 * Supported application languages. Persisted as a BCP-47 tag
 * ({@code MinDisPreferences.languageTag}) so the storage format stays stable
 * if languages are added; this enum is the typed view for UI and logic.
 *
 * <p>Display names are intentionally never translated - every user must be
 * able to recognize their own language.
 */
public enum AppLanguage implements PreferenceEnumValue {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch");

    private final String tag;
    private final String displayName;

    AppLanguage(String tag, String displayName) {
        this.tag = tag;
        this.displayName = displayName;
    }

    public String tag() {
        return tag;
    }

    public Locale locale() {
        return Locale.forLanguageTag(tag);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * @return the language for the tag; {@link #ENGLISH} for unknown tags
     */
    public static AppLanguage fromTag(String languageTag) {
        for (AppLanguage language : values()) {
            if (language.tag.equals(languageTag)) {
                return language;
            }
        }
        return ENGLISH;
    }
}
