package org.mindis.gui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.scene.paint.Color;

import org.mindis.core.preferences.MinDisPreferences;

/**
 * Builds the application's user-agent stylesheet: the base AtlantaFX theme
 * {@code @import}ed, followed by the user's accent/font {@code .root}
 * overrides. Emitted as a single {@code data:} URI for {@link
 * javafx.application.Application#setUserAgentStylesheet}. Applying everything
 * through one user-agent stylesheet (rather than a Scene override layer) keeps
 * design tokens available to popup windows (ComboBox popups etc.), which only
 * consult the user-agent stylesheet.
 *
 * <p>Accent tokens are derived from a single base hex per theme mode, mirroring
 * how AtlantaFX relates {@code -color-accent-fg/emphasis/muted/subtle}: on dark
 * the foreground is a lightened base and muted/subtle darken toward the
 * background; on light it inverts.
 */
public final class ThemeStyler {

    private ThemeStyler() {
    }

    /**
     * @param baseThemeUrl the base theme's stylesheet URL (from
     *                     {@code Theme.getUserAgentStylesheet()})
     * @param accentHex    base accent hex (e.g. {@code #3b82f6}), or
     *                     {@code null} to keep the theme's own accent
     * @return a {@code data:text/css;base64,...} URI that imports the base
     *         theme and appends the accent/font overrides
     */
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

    /** Web hex ({@code #rrggbb}) for a JavaFX color (e.g. the OS accent). */
    public static String toWebHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

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

        if (root.isEmpty()) {
            return "";
        }
        return ".root {\n" + root + "}\n";
    }

    /**
     * JavaFX {@code derive()} lightens (positive) or darkens (negative) a color
     * by a percentage - the same function AtlantaFX themes use for token
     * relationships.
     */
    private static String derive(String base, int percent) {
        return "derive(" + base + ", " + percent + "%)";
    }
}
