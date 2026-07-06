package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.dashboard.DashboardView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Overview module. Content is rebuilt on every activation so the dashboard
 * always reflects the latest roster/services/plan state.
 */
public class DashboardModule extends WorkbenchModule {

    public DashboardModule(String name) {
        super(name);
    }

    @Override
    public Node activate() {
        return new DashboardView();
    }
}
