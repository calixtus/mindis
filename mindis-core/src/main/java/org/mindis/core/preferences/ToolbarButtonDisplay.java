package org.mindis.core.preferences;

import org.mindis.core.l10n.Localization;

/// How the module toolbar buttons render their label and icon: text only, icon
/// only, or both. A user preference (default [#BOTH]), applied app-wide.
public enum ToolbarButtonDisplay implements PreferenceEnumValue {

    TEXT("Text only"),
    ICON("Icons only"),
    BOTH("Text and icons");

    private final String l10nKey;

    ToolbarButtonDisplay(String l10nKey) {
        this.l10nKey = l10nKey;
    }

    @Override
    public String displayName() {
        // Looked up lazily (not cached) so it reflects the current language.
        return Localization.lang(l10nKey);
    }
}
