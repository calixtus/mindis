package org.mindis.core.preferences;

import org.mindis.core.l10n.Localization;

/**
 * Selectable UI accent color. {@link #DEFAULT} follows the operating system's
 * accent color (resolved by the GUI from JavaFX platform preferences); every
 * other value carries a base hex that the GUI derives the AtlantaFX
 * {@code -color-accent-*} tokens from (per light/dark theme).
 *
 * <p>Core stays UI-free: this holds only the hex string. The GUI turns it into
 * CSS. Display names are localized (color words translate cleanly).
 */
public enum AccentColor implements PreferenceEnumValue {
    DEFAULT("Default", null),
    BLUE("Blue", "#3b82f6"),
    GREEN("Green", "#22c55e"),
    PURPLE("Purple", "#8b5cf6"),
    RED("Red", "#ef4444"),
    ORANGE("Orange", "#f97316"),
    TEAL("Teal", "#14b8a6");

    private final String l10nKey;
    private final String baseHex;

    AccentColor(String l10nKey, String baseHex) {
        this.l10nKey = l10nKey;
        this.baseHex = baseHex;
    }

    /**
     * @return the base hex (e.g. {@code #3b82f6}), or {@code null} for
     *         {@link #DEFAULT} (no override; theme decides).
     */
    public String baseHex() {
        return baseHex;
    }

    @Override
    public String displayName() {
        return Localization.lang(l10nKey);
    }
}
