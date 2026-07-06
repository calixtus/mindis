package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.hello.HelloView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Dashboard placeholder. Content is the FxmlKit-loaded HelloView, which keeps
 * the M0 spike (FXML + resource keys + DI controller) exercised inside the
 * workbench shell.
 */
public class DashboardModule extends WorkbenchModule {

    private HelloView helloView;

    public DashboardModule(String name) {
        super(name);
    }

    @Override
    public Node activate() {
        if (helloView == null) {
            helloView = new HelloView();
        }
        return helloView;
    }
}
