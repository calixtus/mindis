package org.mindis.gui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.scene.paint.Color;

import org.mindis.core.preferences.MinDisPreferences;

/// Builds the application's user-agent stylesheet: the base AtlantaFX theme
/// `@import`ed, followed by the user's accent/font `.root`
/// overrides. Emitted as a single `data:` URI for
/// [javafx.application.Application#setUserAgentStylesheet]. Applying everything
/// through one user-agent stylesheet (rather than a Scene override layer) keeps
/// design tokens available to popup windows (ComboBox popups etc.), which only
/// consult the user-agent stylesheet.
///
/// <p>Accent tokens are derived from a single base hex per theme mode, mirroring
/// how AtlantaFX relates `-color-accent-fg/emphasis/muted/subtle`: on dark
/// the foreground is a lightened base and muted/subtle darken toward the
/// background; on light it inverts, the foreground a darkened base.
public final class ThemeStyler {

    private ThemeStyler() {
    }

    /// @param baseThemeUrl the base theme's stylesheet URL (from
    ///                     `Theme.getUserAgentStylesheet()`)
    /// @param accentHex    base accent hex (e.g. `#3b82f6`), or
    ///                     `null` to keep the theme's own accent
    /// @return a `data:text/css;base64,...` URI that imports the base
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

    /// Web hex (`#rrggbb`) for a JavaFX color (e.g. the OS accent).
    public static String toWebHex(Color color) {
        return "#%02x%02x%02x".formatted(
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /// Fallback definitions for legacy Modena tokens (`-fx-control-inner-background`,
    /// `-fx-selection-bar-text`, ...) that GemsFX's bundled control CSS
    /// (CalendarPicker, SearchField/TagsField, TimePicker, ...) looks up but
    /// AtlantaFX's from-scratch `-color-*` stylesheet never defines -
    /// left unresolved, JavaFX logs a ClassCastException/"could not resolve"
    /// warning per lookup and the rule fails to paint. Defined once here
    /// (rather than per-control, as `CalendarPickers` does for rules
    /// this doesn't cover) because it's the only stylesheet popups consult
    /// (see the class javadoc), which per-control author stylesheets don't
    /// reach. Inert for AtlantaFX's own styling of standard controls - they
    /// key off `-color-*` tokens, never these.
    ///
    /// <p>`-fx-selection-bar-text` is GemsFX's row text color in
    /// `search-field-list-view` - applied via ONE unconditional rule to
    /// every row regardless of state (see `search-field.css`), so it
    /// can't differ between idle/hover/selected. Direct rule overrides
    /// targeting the hover/selected `.text` node specifically (tried
    /// first) never took effect: the popup's `ListView` overrides
    /// [javafx.scene.Node#getUserAgentStylesheet()] to return GemsFX's
    /// `search-field.css` directly (see `SearchFieldPopupSkin`),
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
    /// rule, `-fx-accent`/`-fx-selection-bar` are redefined with
    /// a *scope*: pale (`-color-accent-subtle`) only inside
    /// `.search-field-list-view`, versus vivid
    /// (`-color-accent-emphasis`) at `.root` for whatever else
    /// (CalendarPicker, TimePicker, ...) still wants the strong version -
    /// both tokens are Modena-only (AtlantaFX's own controls key off
    /// `-color-*` directly, confirmed against `.button.accent`),
    /// so nothing outside this popup is affected. With hover/selected now
    /// pale rather than saturated, the same blanket
    /// `-fx-selection-bar-text` (`-color-fg-default`, dark)
    /// stays legible across all three row states - no per-state text swap
    /// needed at all.
    ///
    /// <p>The popup's own idle row background needed a separate fix:
    /// `search-field-list-view`'s original rule paints it with
    /// `linear-gradient(derive(-fx-color,-17%), derive(-fx-color,-30%))`
    /// layered under `-fx-control-inner-background` - patching the
    /// tokens that gradient derives from still leaves a *derived*, not flat,
    /// result. This one *is* a safe direct-rule override, unlike the
    /// hover/selected case above: GemsFX's idle-row rule only ever sets
    /// `-fx-background` (an unused intermediate, never converted to
    /// `-fx-background-color` for the idle state), so there's no
    /// competing property value to lose a tie against. Flattened to
    /// `-color-bg-overlay` (AtlantaFX's own popup-surface token),
    /// exactly what `CalendarPickers` does for gemsfx's calendar popup.
    ///
    /// <p>`-fx-box-border` is `TimePicker`'s clock-face popup
    /// (`TimePickerPopup`) crashing the same way `search-field-list-view`
    /// did - `-fx-background-color: -fx-box-border, white` left the first
    /// layer unresolved. Same value `CalendarPickers` already uses for
    /// `.calendar-view`, just global here: `TimePicker` exposes no
    /// popup-content accessor to attach an author-origin stylesheet to
    /// directly the way `CalendarPickers` does via `getCalendarView()`.
    ///
    /// <p>The rest of `time-picker-popup`'s rules aren't unresolved
    /// lookups - they're hardcoded literals (`white`/`gray`/
    /// `lightgray`/`black`), so they don't crash, just ignore the
    /// theme. Tried overriding those too, selector-for-selector matching
    /// `time-picker.css` exactly - confirmed empirically NOT to work:
    /// `TimePickerPopup` (the `HBox` gemsfx shows as the popup
    /// content) overrides `getUserAgentStylesheet()` per-node the same
    /// way SearchField's popup `ListView` does, and per-node
    /// stylesheets win these ties regardless of selector specificity, so the
    /// rule-based override attempt was reverted - it was dead code, not a
    /// partial fix. Only the *selected* cell happens to follow the theme
    /// (purple, matching the app accent) because gemsfx's own rule for it
    /// routes through the `-fx-accent` *token* rather than a literal,
    /// and token inheritance - unlike a competing rule - does cross this
    /// boundary (see the `search-field-list-view` case above). Full
    /// theming of the idle/hover rows would need an author-origin stylesheet
    /// attached directly to the internal `ListView`s the way
    /// `CalendarPickers` does via `getCalendarView()` - but
    /// `TimePicker` exposes no equivalent accessor, and reaching them
    /// would mean reflecting into gemsfx's private fields, too fragile to be
    /// worth it for what's otherwise dead-simple hour/minute lists.
    ///
    /// <p>`-fx-background` and `-fx-control-inner-background-alt`
    /// are the same unresolved-lookup case again, for the [com.dlsc.gemsfx.PowerPane]'s info
    /// center (`info-center-view.css`
    /// paints the notification wrapper, group headers and pinned separator
    /// with them). `InfoCenterView` overrides
    /// `getUserAgentStylesheet()` like the pickers above, so - again -
    /// only the token substitution crosses; the literals gemsfx hardcodes
    /// there (`yellow`/`red`/`green` severity fills) need an
    /// author-origin rule instead and live in `shell/power-pane.css`.
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
              -fx-background: -color-bg-default;
              -fx-control-inner-background-alt: -color-bg-subtle;
            }
            .search-field-list-view {
              -fx-background-color: -color-bg-overlay;
              -fx-accent: -color-accent-subtle;
              -fx-selection-bar: -color-accent-subtle;
            }
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell {
              -fx-background-color: -color-bg-overlay;
            }
            """;

    /// Text and marks (check tick, radio dot, switch knob, progress tick) on accent fills go
    /// through `-color-accent-on`, which [#buildCss] points at
    /// `-color-dark` when [#needsDarkTextOn] says so. AtlantaFX paints that text with
    /// `-color-fg-emphasis` through many per-variant variables (`-color-button-fg`,
    /// `-fg-hover`, `-fg-pressed`, outlined and flat variants...), so instead of overriding
    /// each, `-color-fg-emphasis` itself is redefined inside the controls whose fill is the
    /// accent; lookups resolve from the nearest definition, so everything those controls
    /// derive from it follows. A switch's success and danger fills are not the accent, so
    /// their knob stays light. The date picker's selected day is a direct variable.
    private static final String ACCENT_TEXT_CSS = """
            .root {
              -color-accent-on: -color-light;
            }
            .button:default, .button.accent,
            .menu-button.accent, .split-menu-button.accent,
            .toggle-button:selected,
            .check-box:selected > .box > .mark,
            .radio-button:selected > .radio > .dot,
            .toggle-switch:selected > .thumb,
            .progress-indicator > .determinate-indicator > .tick {
              -color-fg-emphasis: -color-accent-on;
            }
            .toggle-switch:selected:success > .thumb,
            .toggle-switch:selected:danger > .thumb {
              -color-fg-emphasis: -color-light;
            }
            .date-picker-popup {
              -color-date-day-fg-selected: -color-accent-on;
            }
            """;

    static String buildCss(MinDisPreferences.Theme theme, String accentHex,
                           String fontFamily, int fontSize) {
        StringBuilder root = new StringBuilder();

        if (accentHex != null && !accentHex.isBlank()) {
            String base = accentHex;
            boolean dark = theme == MinDisPreferences.Theme.DARK;
            // Light: darker than the base, as in AtlantaFX's own light themes - the base itself
            // reads at about 2.5:1 on the subtle fill a selected sidebar entry sits on.
            String fg = dark ? derive(base, 40) : derive(base, -30);
            String muted = dark ? derive(base, -25) : derive(base, 55);
            String subtle = dark ? derive(base, -55) : derive(base, 80);
            root.append("  -color-accent-fg: ").append(fg).append(";\n");
            root.append("  -color-accent-emphasis: ").append(base).append(";\n");
            root.append("  -color-accent-muted: ").append(muted).append(";\n");
            root.append("  -color-accent-subtle: ").append(subtle).append(";\n");
            if (needsDarkTextOn(base)) {
                root.append("  -color-accent-on: -color-dark;\n");
            }
        }

        if (fontFamily != null && !fontFamily.isBlank()
                && !MinDisPreferences.DEFAULT_FONT_FAMILY.equals(fontFamily)) {
            root.append("  -fx-font-family: \"").append(fontFamily).append("\";\n");
        }
        if (fontSize > 0) {
            root.append("  -fx-font-size: ").append(fontSize).append("px;\n");
        }

        StringBuilder css = new StringBuilder(MODENA_COMPAT_CSS).append(ACCENT_TEXT_CSS);
        if (!root.isEmpty()) {
            css.append(".root {\n").append(root).append("}\n");
        }
        return css.toString();
    }

    /// Whether text on a fill of `accentHex` has to be dark: AtlantaFX always puts its
    /// light text there, which falls below 3:1 on light accents such as green, orange or
    /// teal. 3:1 rather than 4.5:1 so that mid-tone accents (blue, red, purple) keep
    /// the light text the themes were designed with.
    static boolean needsDarkTextOn(String accentHex) {
        return contrast(Color.web(accentHex), LIGHT_TEXT) < 3.0;
    }

    /// AtlantaFX's `-color-light`; Nord Light and Nord Dark differ only in the last digit.
    private static final Color LIGHT_TEXT = Color.web("#fafafc");

    private static double contrast(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /// WCAG 2 relative luminance.
    private static double relativeLuminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /// JavaFX `derive()` lightens (positive) or darkens (negative) a color
    /// by a percentage - the same function AtlantaFX themes use for token
    /// relationships.
    private static String derive(String base, int percent) {
        return "derive(" + base + ", " + percent + "%)";
    }
}
