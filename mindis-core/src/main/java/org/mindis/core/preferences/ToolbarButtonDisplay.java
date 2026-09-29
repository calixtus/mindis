package org.mindis.core.preferences;

import org.mindis.core.l10n.Localization;

/// How the module toolbar buttons render their label and icon: text only, icon
/// only, or both. A user preference (default [#BOTH]), applied app-wide.
public enum ToolbarButtonDisplay implements PreferenceEnumValue {

    TEXT,
    ICON,
    BOTH;

    @Override
    public String displayName() {
        // Looked up per call (not stored) so it reflects the current language, and written
        // as a literal inside lang(...) so LocalizationConsistencyTest can find the key.
        return switch (this) {
            case TEXT -> Localization.lang("Text only");
            case ICON -> Localization.lang("Icons only");
            case BOTH -> Localization.lang("Text and icons");
        };
    }
}
