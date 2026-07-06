package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.services.ServicesView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Liturgical services and weekly templates module.
 */
public class ServicesModule extends WorkbenchModule {

    private ServicesView view;

    public ServicesModule(String name) {
        super(name);
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new ServicesView();
        }
        return view;
    }
}
