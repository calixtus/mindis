package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.roles.RolesView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Liturgical role management module (configurable roles + age requirements).
 */
public class RolesModule extends WorkbenchModule {

    private RolesView view;

    public RolesModule(String name) {
        super(name, "mdi2t-tag-multiple");
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new RolesView();
        }
        return view;
    }
}
