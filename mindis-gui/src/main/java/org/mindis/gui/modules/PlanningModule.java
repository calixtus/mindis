package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.planning.PlanningView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Automatic planning module: solve, pin, re-solve, save.
 */
public class PlanningModule extends WorkbenchModule {

    private PlanningView view;

    public PlanningModule(String name) {
        super(name, "mdi2c-calendar-check");
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new PlanningView();
        } else if (view.getController() != null) {
            // Pick up roster/service edits from other modules; keeps the
            // current slot decisions (see PlanningController).
            view.getController().refreshFromRepositories();
        }
        return view;
    }
}
