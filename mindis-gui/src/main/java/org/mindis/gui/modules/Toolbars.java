package org.mindis.gui.modules;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;

import org.kordamp.ikonli.javafx.FontIcon;

/// Builders for the module toolbar buttons, so they all carry an icon and the
/// `toolbar-button` style class the app-wide "text / icon / both" display
/// setting keys off (see `shell.css` and
/// [org.mindis.core.preferences.ToolbarButtonDisplay]).
final class Toolbars {

    private Toolbars() {
    }

    /// A toolbar button with the given localized text and Material Design icon.
    /// The text is also installed as a tooltip so the button still names its
    /// action in "icon only" mode, where the label is hidden (see
    /// `shell.css` and [org.mindis.core.preferences.ToolbarButtonDisplay]).
    static Button button(String text, String iconLiteral) {
        Button button = new Button(text, new FontIcon(iconLiteral));
        button.setTooltip(new Tooltip(text));
        markToolbarButton(button, iconLiteral);
        return button;
    }

    /// Adds an icon (if the button has none) and the toolbar-button style class
    /// to an already-built button-like control (e.g. a `SplitMenuButton`).
    static void markToolbarButton(ButtonBase button, String iconLiteral) {
        if (button.getGraphic() == null) {
            button.setGraphic(new FontIcon(iconLiteral));
        }
        button.getStyleClass().add("toolbar-button");
    }
}
