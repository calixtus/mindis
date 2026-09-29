package org.mindis.core.l10n;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Localization with full-text keys (JabRef style): the key IS the English text.
/// Missing translations fall back to the key itself, so the UI never shows raw keys.
///
/// <p>Positional parameters use `%0`, `%1`, ... placeholders:
/// <pre>`Localization.lang("%0 of %1 slots assigned", assigned, total)`</pre>
///
/// <p>Deliberate DIP exception (PLAN.md section 8): a global mutable static,
/// like JabRef's Localization. Fine for a single-user desktop process with one
/// locale; a future web module needs per-request locale resolution instead -
/// tracked as an open decision in ADR-003.
public final class Localization {

    private static final Logger LOGGER = LoggerFactory.getLogger(Localization.class);
    private static final String BUNDLE_BASE_NAME = "org.mindis.core.l10n.MinDis";

    /// The languages that have a bundle next to this class. There is no unsuffixed
    /// `MinDis.properties`: as in JabRef, English is a language like any other and lives in
    /// `MinDis_en.properties`. A locale is therefore resolved to one of these before the
    /// bundle is loaded, instead of an unsupported locale silently falling through to a
    /// base file that would have to be kept in sync with the English one by hand.
    private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN);

    private static volatile ResourceBundle bundle = loadBundle(Locale.getDefault());

    private Localization() {
    }

    public static void setLocale(Locale locale) {
        Locale.setDefault(locale);
        bundle = loadBundle(locale);
    }

    public static ResourceBundle getBundle() {
        return bundle;
    }

    /// The locales a bundle exists for. `LocalizationConsistencyTest` asserts that this,
    /// the files on disk and `AppLanguage` all name the same set.
    public static List<Locale> supportedLocales() {
        return SUPPORTED_LOCALES;
    }

    /// @return the supported locale sharing `locale`'s language, English otherwise - so a
    ///         French desktop gets English rather than a missing bundle
    public static Locale resolveSupportedLocale(Locale locale) {
        return SUPPORTED_LOCALES.stream()
                .filter(supported -> supported.getLanguage().equals(locale.getLanguage()))
                .findFirst()
                .orElse(Locale.ENGLISH);
    }

    public static String lang(String englishText, @Nullable Object... parameters) {
        String translation;
        try {
            translation = bundle.getString(englishText);
        } catch (MissingResourceException e) {
            LOGGER.debug("No translation for key: {}", englishText);
            translation = englishText;
        }
        for (int i = 0; i < parameters.length; i++) {
            translation = translation.replace("%" + i, String.valueOf(parameters[i]));
        }
        return translation;
    }

    /// Translates text that is only known at runtime - the export template's `lang(...)`
    /// function, whose argument comes out of a `.peb` file the user may have edited, so no
    /// literal in this code base can name it.
    ///
    /// <p>Not for keys written in the code: those must be literals inside [#lang] or
    /// `LocalizationConsistencyTest` cannot see them. That test asserts this method is
    /// called from the template bridge and nowhere else.
    public static String translateDynamic(String text) {
        return bundle.containsKey(text) ? bundle.getString(text) : text;
    }

    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, resolveSupportedLocale(locale));
    }
}
