package org.mindis.workbench;

import javafx.scene.Node;

/**
 * One functional area of the application (Dashboard, Servers, ...), reachable
 * through a permanent sidebar entry in the {@link Workbench}.
 *
 * <p>Lifecycle (modeled on WorkbenchFX's WorkbenchModule):
 * <ol>
 *   <li>{@link #activate()} - called every time the module is selected in the
 *       sidebar; returns the content node (fresh or cached, the module
 *       decides).
 *   <li>{@link #deactivate()} - called when another module is selected.
 *   <li>{@link #destroy()} - reserved for a closing hook (return {@code false}
 *       to veto); not called by the sidebar shell, which keeps all modules
 *       available.
 * </ol>
 */
public abstract class WorkbenchModule {

    private final String name;
    private final String iconLiteral;

    protected WorkbenchModule(String name) {
        this(name, null);
    }

    /**
     * @param iconLiteral Ikonli icon literal (e.g. {@code "mdi2v-view-dashboard"});
     *                    {@code null} for a text-only sidebar entry
     */
    protected WorkbenchModule(String name, String iconLiteral) {
        this.name = name;
        this.iconLiteral = iconLiteral;
    }

    public final String getName() {
        return name;
    }

    public final String getIconLiteral() {
        return iconLiteral;
    }

    public abstract Node activate();

    public void deactivate() {
    }

    public boolean destroy() {
        return true;
    }
}
