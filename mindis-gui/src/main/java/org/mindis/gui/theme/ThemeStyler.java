package org.mindis.gui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javafx.scene.paint.Color;

import org.mindis.core.preferences.MinDisPreferences;

/**
 * Builds the per-user customization stylesheet layered on top of the base
 * AtlantaFX theme: accent color and application font. Emitted as a
 * {@code data:} URI so it can be dropped straight into a Scene's stylesheet
 * list (Scene stylesheets override the user-agent theme). Same layering trick
 * the AtlantaFX sampler uses.
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
     * @param accentHex base accent hex (e.g. {@code #3b82f6}), or {@code null}
     *                  to leave the theme's own accent untouched
     * @return a {@code data:text/css;base64,...} URI for {@link
     *         javafx.scene.Scene#getStylesheets()}, or an empty string when
     *         nothing overrides the base theme.
     */
    public static String buildStylesheetUri(MinDisPreferences.Theme theme,
                                            String accentHex,
                                            String fontFamily,
                                            int fontSize) {
        String css = buildCss(theme, accentHex, fontFamily, fontSize);
        if (css.isEmpty()) {
            return "";
        }
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
