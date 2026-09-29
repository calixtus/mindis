package org.mindis.core.preferences;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;

/// Selectable UI accent color. [#DEFAULT] follows the operating system's
/// accent color (resolved by the GUI from JavaFX platform preferences); every
/// other value carries a base hex that the GUI derives the AtlantaFX
/// `-color-accent-*` tokens from (per light/dark theme).
///
/// <p>Core stays UI-free: this holds only the hex string. The GUI turns it into
/// CSS. Display names are localized (color words translate cleanly).
public enum AccentColor implements PreferenceEnumValue {
    DEFAULT(null),
    BLUE("#3b82f6"),
    GREEN("#22c55e"),
    PURPLE("#8b5cf6"),
    RED("#ef4444"),
    ORANGE("#f97316"),
    TEAL("#14b8a6");

    private final @Nullable String baseHex;

    AccentColor(@Nullable String baseHex) {
        this.baseHex = baseHex;
    }

    /// @return the base hex (e.g. `#3b82f6`), or `null` for
    ///         [#DEFAULT] (no override; theme decides).
    public @Nullable String baseHex() {
        return baseHex;
    }

    @Override
    public String displayName() {
        // Looked up per call (not stored) so it reflects the current language, and written
        // as a literal inside lang(...) so LocalizationConsistencyTest can find the key.
        return switch (this) {
            case DEFAULT -> Localization.lang("Default");
            case BLUE -> Localization.lang("Blue");
            case GREEN -> Localization.lang("Green");
            case PURPLE -> Localization.lang("Purple");
            case RED -> Localization.lang("Red");
            case ORANGE -> Localization.lang("Orange");
            case TEAL -> Localization.lang("Teal");
        };
    }
}
