package org.mindis.gui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.scene.paint.Color;

import org.mindis.core.preferences.MinDisPreferences;

/// Builds the application's user-agent stylesheet: the base AtlantaFX theme
/// {@code @import}ed, followed by the user's accent/font {@code .root}
/// overrides. Emitted as a single {@code data:} URI for {@link
/// javafx.application.Application#setUserAgentStylesheet}. Applying everything
/// through one user-agent stylesheet (rather than a Scene override layer) keeps
/// design tokens available to popup windows (ComboBox popups etc.), which only
/// consult the user-agent stylesheet.
///
/// <p>Accent tokens are derived from a single base hex per theme mode, mirroring
/// how AtlantaFX relates {@code -color-accent-fg/emphasis/muted/subtle}: on dark
/// the foreground is a lightened base and muted/subtle darken toward the
/// background; on light it inverts.
public final class ThemeStyler {

    private ThemeStyler() {
    }

    /// @param baseThemeUrl the base theme's stylesheet URL (from
    ///                     {@code Theme.getUserAgentStylesheet()})
    /// @param accentHex    base accent hex (e.g. {@code #3b82f6}), or
    ///                     {@code null} to keep the theme's own accent
    /// @return a {@code data:text/css;base64,...} URI that imports the base
    ///         theme and appends the accent/font overrides
    public static String userAgentStylesheet(String baseThemeUrl,
                                             MinDisPreferences.Theme theme,
                                             String accentHex,
                                             String fontFamily,
                                             int fontSize) {
        String css = "@import \"" + baseThemeUrl + "\";\n"
                + buildCss(theme, accentHex, fontFamily, fontSize);
        return "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /// Web hex ({@code #rrggbb}) for a JavaFX color (e.g. the OS accent).
    public static String toWebHex(Color color) {
        return "#%02x%02x%02x".formatted(
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /// Fallback definitions for legacy Modena tokens ({@code -fx-control-inner-background},
    /// {@code -fx-selection-bar-text}, ...) that GemsFX's bundled control CSS
    /// (CalendarPicker, SearchField/TagsField, TimePicker, ...) looks up but
    /// AtlantaFX's from-scratch {@code -color-*} stylesheet never defines -
    /// left unresolved, JavaFX logs a ClassCastException/"could not resolve"
    /// warning per lookup and the rule fails to paint. Defined once here
    /// (rather than per-control, as {@code CalendarPickers} does for rules
    /// this doesn't cover) because it's the only stylesheet popups consult
    /// (see the class javadoc), which per-control author stylesheets don't
    /// reach. Inert for AtlantaFX's own styling of standard controls - they
    /// key off {@code -color-*} tokens, never these.
    ///
    /// <p>{@code -fx-selection-bar-text} is GemsFX's row text color in
    /// {@code search-field-list-view} - applied via ONE unconditional rule to
    /// every row regardless of state (see {@code search-field.css}), so it
    /// can't differ between idle/hover/selected. Direct rule overrides
    /// targeting the hover/selected {@code .text} node specifically (tried
    /// first) never took effect: the popup's {@code ListView} overrides
    /// {@link javafx.scene.Node#getUserAgentStylesheet()} to return GemsFX's
    /// {@code search-field.css} directly (see {@code SearchFieldPopupSkin}),
    /// and per-Node user-agent stylesheets win property-for-property ties
    /// against the application-wide one from here, regardless of selector
    /// specificity - confirmed empirically (background token substitutions
    /// always took effect; competing background/fill *rules* for the same
    /// property never did).
    ///
    /// <p>What does reliably cross that boundary is custom-property
    /// (token) *inheritance*, since that's resolved by the normal CSS
    /// cascade rather than a property-value tie - the crash-fix tokens below
    /// prove it (zero resolution warnings). So instead of fighting for a
    /// rule, {@code -fx-accent}/{@code -fx-selection-bar} are redefined with
    /// a *scope*: pale ({@code -color-accent-subtle}) only inside
    /// {@code .search-field-list-view}, versus vivid
    /// ({@code -color-accent-emphasis}) at {@code .root} for whatever else
    /// (CalendarPicker, TimePicker, ...) still wants the strong version -
    /// both tokens are Modena-only (AtlantaFX's own controls key off
    /// {@code -color-*} directly, confirmed against {@code .button.accent}),
    /// so nothing outside this popup is affected. With hover/selected now
    /// pale rather than saturated, the same blanket
    /// {@code -fx-selection-bar-text} ({@code -color-fg-default}, dark)
    /// stays legible across all three row states - no per-state text swap
    /// needed at all.
    ///
    /// <p>The popup's own idle row background needed a separate fix:
    /// {@code search-field-list-view}'s original rule paints it with
    /// {@code linear-gradient(derive(-fx-color,-17%), derive(-fx-color,-30%))}
    /// layered under {@code -fx-control-inner-background} - patching the
    /// tokens that gradient derives from still leaves a *derived*, not flat,
    /// result. This one *is* a safe direct-rule override, unlike the
    /// hover/selected case above: GemsFX's idle-row rule only ever sets
    /// {@code -fx-background} (an unused intermediate, never converted to
    /// {@code -fx-background-color} for the idle state), so there's no
    /// competing property value to lose a tie against. Flattened to
    /// {@code -color-bg-overlay} (AtlantaFX's own popup-surface token),
    /// exactly what {@code CalendarPickers} does for gemsfx's calendar popup.
    ///
    /// <p>{@code -fx-box-border} is {@code TimePicker}'s clock-face popup
    /// ({@code TimePickerPopup}) crashing the same way {@code search-field-list-view}
    /// did - {@code -fx-background-color: -fx-box-border, white} left the first
    /// layer unresolved. Same value {@code CalendarPickers} already uses for
    /// {@code .calendar-view}, just global here: {@code TimePicker} exposes no
    /// popup-content accessor to attach an author-origin stylesheet to
    /// directly the way {@code CalendarPickers} does via {@code getCalendarView()}.
    ///
    /// <p>The rest of {@code time-picker-popup}'s rules aren't unresolved
    /// lookups - they're hardcoded literals ({@code white}/{@code gray}/
    /// {@code lightgray}/{@code black}), so they don't crash, just ignore the
    /// theme. Tried overriding those too, selector-for-selector matching
    /// {@code time-picker.css} exactly - confirmed empirically NOT to work:
    /// {@code TimePickerPopup} (the {@code HBox} gemsfx shows as the popup
    /// content) overrides {@code getUserAgentStylesheet()} per-node the same
    /// way SearchField's popup {@code ListView} does, and per-node
    /// stylesheets win these ties regardless of selector specificity, so the
    /// rule-based override attempt was reverted - it was dead code, not a
    /// partial fix. Only the *selected* cell happens to follow the theme
    /// (purple, matching the app accent) because gemsfx's own rule for it
    /// routes through the {@code -fx-accent} *token* rather than a literal,
    /// and token inheritance - unlike a competing rule - does cross this
    /// boundary (see the {@code search-field-list-view} case above). Full
    /// theming of the idle/hover rows would need an author-origin stylesheet
    /// attached directly to the internal {@code ListView}s the way
    /// {@code CalendarPickers} does via {@code getCalendarView()} - but
    /// {@code TimePicker} exposes no equivalent accessor, and reaching them
    /// would mean reflecting into gemsfx's private fields, too fragile to be
    /// worth it for what's otherwise dead-simple hour/minute lists.
    private static final String MODENA_COMPAT_CSS = """
            .root {
              -fx-control-inner-background: -color-bg-default;
              -fx-text-background-color: -color-fg-default;
              -fx-text-inner-color: -color-fg-default;
              -fx-selection-bar: -color-accent-emphasis;
              -fx-selection-bar-text: -color-fg-default;
              -fx-cell-focus-inner-border: -color-border-default;
              -fx-accent: -color-accent-emphasis;
              -fx-color: -color-bg-default;
              -fx-base: -color-bg-default;
              -fx-box-border: -color-border-default;
            }
            .search-field-list-view {
              -fx-background-color: -color-bg-overlay;
              -fx-accent: -color-accent-subtle;
              -fx-selection-bar: -color-accent-subtle;
            }
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell {
              -fx-background-color: -color-bg-overlay;
            }
            .tile .title {
              -fx-wrap-text: false;
              -fx-text-overrun: ellipsis;
            }
            .altar-warning-icon {
              -fx-icon-color: -color-danger-fg;
            }
            .field-changed {
              -fx-border-color: -color-accent-emphasis;
              -fx-border-width: 0 0 0 3;
              -fx-border-insets: 0;
              -fx-padding: 0 0 0 6;
            }
            .services-tile-table .column-header-background {
              -fx-max-height: 0;
              -fx-pref-height: 0;
              visibility: hidden;
            }
            .services-tile-table .table-row-cell {
              -fx-border-color: transparent transparent -color-border-default transparent;
              -fx-border-width: 0 0 1 0;
            }
            .service-tile-datetime {
              -fx-font-size: 1.3em;
              -fx-font-weight: bold;
            }
            .service-tile-role {
              -fx-font-weight: bold;
              -fx-text-fill: -color-fg-muted;
            }
            .service-tile-archived {
              -fx-opacity: 0.55;
            }
            """;

    static String buildCss(MinDisPreferences.Theme theme, String accentHex,
                           String fontFamily, int fontSize) {
        StringBuilder root = new StringBuilder();

        if (accentHex != null && !accentHex.isBlank()) {
            String base = accentHex;
            boolean dark = theme == MinDisPreferences.Theme.DARK;
            String fg = dark ? derive(base, 40) : base;
            String muted = dark ? derive(base, -25) : derive(base, 55);
            String subtle = dark ? derive(base, -55) : derive(base, 80);
            root.append("  -color-accent-fg: ").append(fg).append(";\n");
            root.append("  -color-accent-emphasis: ").append(base).append(";\n");
            root.append("  -color-accent-muted: ").append(muted).append(";\n");
            root.append("  -color-accent-subtle: ").append(subtle).append(";\n");
        }

        if (fontFamily != null && !fontFamily.isBlank()
                && !MinDisPreferences.DEFAULT_FONT_FAMILY.equals(fontFamily)) {
            root.append("  -fx-font-family: \"").append(fontFamily).append("\";\n");
        }
        if (fontSize > 0) {
            root.append("  -fx-font-size: ").append(fontSize).append("px;\n");
        }

        StringBuilder css = new StringBuilder(MODENA_COMPAT_CSS);
        if (!root.isEmpty()) {
            css.append(".root {\n").append(root).append("}\n");
        }
        return css.toString();
    }

    /// JavaFX {@code derive()} lightens (positive) or darkens (negative) a color
    /// by a percentage - the same function AtlantaFX themes use for token
    /// relationships.
    private static String derive(String base, int percent) {
        return "derive(" + base + ", " + percent + "%)";
    }
}
