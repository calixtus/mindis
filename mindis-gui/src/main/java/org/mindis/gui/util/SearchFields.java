package org.mindis.gui.util;

import com.dlsc.gemsfx.SearchField;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/// Corrective theming for GemsFX [SearchField] (and its `
/// TagsField` subclass), same problem and same fix as [CalendarPickers]:
/// gemsfx's bundled `search-field.css` is written against stock Modena
/// (`-fx-control-inner-background`, `-fx-text-background-color`,
/// ...), tokens AtlantaFX's from-scratch `-color-*` stylesheet never
/// defines. Left alone, resolving them throws a ClassCastException in the
/// javafx.css log the moment the field is shown.
public final class SearchFields {

    /// Attached directly to the field (author origin) rather than folded into
    /// the app's user-agent stylesheet - author origin always outranks
    /// gemsfx's own `search-field.css` (user-agent origin) regardless
    /// of selector specificity, so there's no cascade tie to fight.
    private static final String SEARCH_FIELD_THEME_CSS = """
            .search-field .graphic-wrapper {
              -fx-background-color: -color-bg-default;
            }
            .search-field .graphic-wrapper .ikonli-font-icon {
              -fx-icon-color: -color-fg-default;
            }
            .search-field .graphic-wrapper .history-button > .icon {
              -fx-background-color: -color-fg-default;
            }
            """;

    private static final String SEARCH_FIELD_THEME_STYLESHEET = "data:text/css;base64,"
            + Base64.getEncoder().encodeToString(SEARCH_FIELD_THEME_CSS.getBytes(StandardCharsets.UTF_8));

    private SearchFields() {
    }

    /// Attaches the corrective stylesheet to `field` so it follows the app's AtlantaFX theme.
    public static void applyTheme(SearchField<?> field) {
        field.getStylesheets().add(SEARCH_FIELD_THEME_STYLESHEET);
    }
}
