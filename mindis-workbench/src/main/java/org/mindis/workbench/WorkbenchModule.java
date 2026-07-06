package org.mindis.workbench;

import javafx.scene.Node;

/**
 * One functional area of the application (Dashboard, Servers, ...), presented
 * as a closable tab in the {@link Workbench}.
 *
 * <p>Lifecycle (modeled on WorkbenchFX's WorkbenchModule):
 * <ol>
 *   <li>{@link #activate()} - called every time the module is opened or its
 *       tab is selected; returns the content node (fresh or cached, the
 *       module decides).
 *   <li>{@link #deactivate()} - called when another module's tab is selected.
 *   <li>{@link #destroy()} - called when the tab is closed; return
 *       {@code false} to veto (e.g. unsaved changes).
 * </ol>
 */
public abstract class WorkbenchModule {

    private final String name;

    protected WorkbenchModule(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public abstract Node activate();

    public void deactivate() {
    }

    public boolean destroy() {
        return true;
    }
}
