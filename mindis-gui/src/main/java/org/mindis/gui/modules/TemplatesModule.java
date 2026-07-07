package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.templates.TemplatesView;
import org.mindis.workbench.WorkbenchModule;

/**
 * Weekly service templates module. Extracted from Services so template
 * management (currently weekly-only; month/year/feast-day types planned) has
 * its own sidebar entry.
 */
public class TemplatesModule extends WorkbenchModule {

    private TemplatesView view;

    public TemplatesModule(String name) {
        super(name, "mdi2c-calendar-sync");
    }

    @Override
    public Node activate() {
        if (view == null) {
            view = new TemplatesView();
        }
        return view;
    }
}
