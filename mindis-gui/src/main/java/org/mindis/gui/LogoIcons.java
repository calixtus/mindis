package org.mindis.gui;

import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;

/// The stock icon catalog a collection can pick from when it has no custom image
/// logo (see {@link org.mindis.core.model.CollectionMeta#logoIcon()}), plus the
/// default. Literals are Material Design Icons (the `materialdesign2` Ikonli
/// pack the app already ships).
final class LogoIcons {

    /// Drawn when a collection has neither a custom image nor a chosen icon.
    static final String DEFAULT = "mdi2c-church";

    /// The options offered in the icon dropdown, default first.
    static final List<String> CATALOG = List.of(
            "mdi2c-church",
            "mdi2c-cross",
            "mdi2s-star",
            "mdi2h-heart",
            "mdi2h-home",
            "mdi2b-bell",
            "mdi2a-account-group",
            "mdi2b-book-open-variant",
            "mdi2c-calendar",
            "mdi2m-music");

    private LogoIcons() {
    }

    /// A sized icon node for {@code literal}, falling back to {@link #DEFAULT}
    /// if the literal is unknown (a bad stored/hand-edited value must never crash
    /// the switcher or the editor).
    static FontIcon iconNode(String literal, int size) {
        FontIcon icon;
        try {
            icon = new FontIcon(literal);
        } catch (RuntimeException e) {
            icon = new FontIcon(DEFAULT);
        }
        icon.setIconSize(size);
        icon.getStyleClass().add("collection-logo-placeholder");
        return icon;
    }

    /// A human-readable name for a catalog literal, derived from the literal
    /// (e.g. {@code mdi2a-account-group} -> {@code Account group}).
    static String displayName(String literal) {
        int dash = literal.indexOf('-');
        String name = dash < 0 ? literal : literal.substring(dash + 1);
        name = name.replace('-', ' ');
        return name.isEmpty() ? literal : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
