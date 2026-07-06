package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.servers.ServersView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Roster management module.
 */
public class ServersModule extends WorkbenchModule {

    private ServersView view;

    public ServersModule(String name) {
        super(name, "mdi2a-account-group");
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new ServersView();
        }
        return view;
    }
}
