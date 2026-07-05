package org.mindis.core.l10n;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Localization with full-text keys (JabRef style): the key IS the English text.
 * Missing translations fall back to the key itself, so the UI never shows raw keys.
 *
 * <p>Positional parameters use {@code %0}, {@code %1}, ... placeholders:
 * <pre>{@code Localization.lang("%0 of %1 slots assigned", assigned, total)}</pre>
 */
public final class Localization {

    private static final Logger LOGGER = Logger.getLogger(Localization.class.getName());
    private static final String BUNDLE_BASE_NAME = "org.mindis.core.l10n.MinDis";

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

    public static String lang(String englishText, Object... parameters) {
        String translation;
        try {
            translation = bundle.getString(englishText);
        } catch (MissingResourceException e) {
            LOGGER.log(Level.FINE, "No translation for key: {0}", englishText);
            translation = englishText;
        }
        for (int i = 0; i < parameters.length; i++) {
            translation = translation.replace("%" + i, String.valueOf(parameters[i]));
        }
        return translation;
    }

    private static ResourceBundle loadBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
