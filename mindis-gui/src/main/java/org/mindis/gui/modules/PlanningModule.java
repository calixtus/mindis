package org.mindis.gui.modules;

import javafx.scene.Node;

import org.jspecify.annotations.Nullable;

import org.mindis.gui.planning.PlanningView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Automatic planning module: solve, pin, re-solve, save.
 */
public class PlanningModule extends WorkbenchModule {

    private @Nullable PlanningView view;

    public PlanningModule(String name) {
        super(name, "mdi2c-calendar-check");
    }

    @Override
    public Node activate() {
        PlanningView content = view;
        if (content == null) {
            content = new PlanningView();
            view = content;
        } else if (content.getController() != null) {
            // Pick up roster/service edits from other modules; keeps the
            // current slot decisions (see PlanningController).
            content.getController().refreshFromRepositories();
        }
        return content;
    }
}
