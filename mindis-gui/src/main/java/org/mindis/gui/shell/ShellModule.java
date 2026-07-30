package org.mindis.gui.shell;

import javafx.scene.Node;

import org.jspecify.annotations.Nullable;

/// One functional area of the application (Dashboard, Servers, ...), reachable
/// through a permanent sidebar entry in the [AppShell].
///
/// <p>Lifecycle:
/// <ol>
///   <li>[#activate()] - called every time the module is selected in the
///       sidebar; returns the content node (fresh or cached, the module
///       decides).
///   <li>[#deactivate()] - called when another module is selected.
///   <li>[#destroy()] - reserved for a closing hook (return `false`
///       to veto); not called by the sidebar shell, which keeps all modules
///       available.
///   <li>[#dispose()] - called when the module instance is discarded for
///       good (e.g. a full UI rebuild replaces every module); detach any
///       listeners registered on objects that outlive the module (shared
///       [org.mindis.gui.data.LiveStore]s), or the discarded module
///       graph stays reachable.
/// </ol>
public abstract class ShellModule {

    private final String name;
    private final @Nullable String iconLiteral;

    protected ShellModule(String name) {
        this(name, null);
    }

    /// @param iconLiteral Ikonli icon literal (e.g. `"mdi2v-view-dashboard"`);
    ///                    `null` for a text-only sidebar entry
    protected ShellModule(String name, @Nullable String iconLiteral) {
        this.name = name;
        this.iconLiteral = iconLiteral;
    }

    public final String getName() {
        return name;
    }

    public final @Nullable String getIconLiteral() {
        return iconLiteral;
    }

    public abstract Node activate();

    public void deactivate() {
    }

    public boolean destroy() {
        return true;
    }

    /// Detaches everything this module registered on longer-lived objects;
    /// called once when the instance is discarded (never reactivated after).
    public void dispose() {
    }
}
