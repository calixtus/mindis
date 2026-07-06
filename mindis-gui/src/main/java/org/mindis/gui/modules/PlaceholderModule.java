package org.mindis.gui.modules;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.workbench.WorkbenchModule;

/**
 * Placeholder for functional areas arriving in later milestones (M2+).
 */
public class PlaceholderModule extends WorkbenchModule {

    public PlaceholderModule(String name) {
        super(name);
    }

    @Override
    public Node activate() {
        Label title = new Label(getName());
        title.setStyle("-fx-font-size: 2em;");
        Label hint = new Label(Localization.lang("This area arrives with a later milestone."));
        VBox content = new VBox(12, title, hint);
        content.setAlignment(Pos.CENTER);
        return content;
    }
}
