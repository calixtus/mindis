package org.mindis.gui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import atlantafx.base.theme.Theme;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.MinDisPreferences;

/// The two layers MinDis puts on the AtlantaFX base theme, both installed through
/// AtlantaFX's `ThemeManager` (see `MinDisApp`):
///
/// - [#withModenaTokens]: the theme as the user-agent stylesheet, plus the Modena tokens
///   GemsFX looks up, which must resolve in every window from its very first style pass.
/// - [#stylesheet]: the user's accent and font as `.root` overrides, text on accent
///   fills, and the search popup's rules. `ThemeManager` puts it on every window's scene
///   as the window opens, popups and dialogs included, so it is an author stylesheet and
///   outranks both the theme and the per-control user-agent stylesheets GemsFX's popups
///   install.
///
/// <p>Accent tokens are derived from a single base hex per theme mode, mirroring
/// how AtlantaFX relates `-color-accent-fg/emphasis/muted/subtle`: on dark
/// the foreground is a lightened base and muted/subtle darken toward the
/// background; on light it inverts, the foreground a darkened base.
public final class ThemeStyler {

    /// What the stylesheet is built from.
    ///
    /// @param theme      LIGHT or DARK; SYSTEM is resolved before it gets here
    /// @param accentHex  base accent hex (e.g. `#3b82f6`)
    /// @param fontFamily the user's font, or [MinDisPreferences#DEFAULT_FONT_FAMILY] for the theme's
    /// @param fontSize   font size in px, or 0 for the theme's
    public record Appearance(MinDisPreferences.Theme theme, String accentHex, String fontFamily, int fontSize) {
    }

    /// Key under which [#apply] remembers, on the scene, the stylesheet it added there.
    private static final Object APPLIED_STYLESHEET = new Object();

    private ThemeStyler() {
    }

    /// Puts the stylesheet for `appearance` on `scene`, replacing the one an earlier call
    /// put there; `null` only removes it.
    public static void apply(Scene scene, @Nullable Appearance appearance) {
        if (scene.getProperties().remove(APPLIED_STYLESHEET) instanceof String previous) {
            scene.getStylesheets().remove(previous);
        }
        if (appearance != null) {
            String stylesheet = stylesheet(appearance);
            scene.getStylesheets().add(stylesheet);
            scene.getProperties().put(APPLIED_STYLESHEET, stylesheet);
        }
    }

    /// The stylesheet as a `data:` URI. Base64 rather than AtlantaFX's `Styles.encode`,
    /// whose plain form JavaFX percent-decodes - and `derive()` takes percentages.
    public static String stylesheet(Appearance appearance) {
        String css = buildCss(appearance.theme(), appearance.accentHex(), appearance.fontFamily(),
                appearance.fontSize());
        return encode(css);
    }

    private static String encode(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /// Web hex (`#rrggbb`) for a JavaFX color (e.g. the OS accent).
    public static String toWebHex(Color color) {
        return "#%02x%02x%02x".formatted(
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /// `base` with the Modena tokens added to its user-agent stylesheet, for
    /// `ThemeManager` to install in place of `base` itself.
    ///
    /// <p>The tokens have to be in the user-agent layer: GemsFX's popups run their first
    /// style pass inside their own `show()`, before the window is registered and so before
    /// `ThemeManager` puts [#stylesheet] on it, and only the user-agent stylesheet already
    /// applies then. The theme comes in as CSS text through AtlantaFX's `stylesheet:` URL,
    /// every module listed, rather than by its `.css` path, for which JavaFX substitutes
    /// the precompiled `.bss` - and JavaFX 27 fails with an NPE when a text stylesheet
    /// imports a binary one. Parsing the text costs some 10-40ms per theme switch.
    public static Theme withModenaTokens(Theme base) {
        String css = "@import \"" + base.getUserAgentStylesheet(base.getManifest().getModules().keySet()) + "\";\n"
                + MODENA_TOKENS_CSS;
        return Theme.of(base.getName(), encode(css), base.isDarkMode());
    }

    /// Legacy Modena tokens (`-fx-control-inner-background`, `-fx-selection-bar-text`, ...)
    /// that GemsFX's bundled control CSS - CalendarPicker, SearchField, TimePicker, the
    /// PowerPane's info center - looks up but AtlantaFX never defines. Left unresolved,
    /// JavaFX logs a warning per lookup and the rule paints nothing. Inert for AtlantaFX's
    /// own controls, which key off `-color-*` only.
    private static final String MODENA_TOKENS_CSS = """
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
            """;

    /// The search-field popup paints every row's text with one unconditional
    /// `-fx-selection-bar-text`, so its hover and selection fills are kept pale
    /// (`-fx-accent`/`-fx-selection-bar` redefined inside it, vivid everywhere else) for
    /// that one dark text colour to read on all three. Its idle rows are flattened to
    /// `-color-bg-overlay`, AtlantaFX's popup surface, instead of GemsFX's gradient derived
    /// from `-fx-color`; because this stylesheet outranks GemsFX's, the hover and selected
    /// fills are restated after it, or the flat idle fill would cover them too.
    private static final String SEARCH_POPUP_CSS = """
            .search-field-list-view {
              -fx-background-color: -color-bg-overlay;
              -fx-accent: -color-accent-subtle;
              -fx-selection-bar: -color-accent-subtle;
            }
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell {
              -fx-background-color: -color-bg-overlay;
            }
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:filled:hover {
              -fx-background-color: -fx-selection-bar;
            }
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:filled:selected,
            .search-field-list-view > .virtual-flow > .clipped-container > .sheet > .list-cell:filled:selected:hover {
              -fx-background-color: -fx-background, -fx-cell-focus-inner-border, -fx-background;
              -fx-background-insets: 0, 1, 2;
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

        StringBuilder css = new StringBuilder(SEARCH_POPUP_CSS).append(ACCENT_TEXT_CSS);
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
