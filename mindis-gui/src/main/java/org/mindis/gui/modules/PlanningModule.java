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
        super(name);
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new PlanningView();
        }
        return view;
    }
}
