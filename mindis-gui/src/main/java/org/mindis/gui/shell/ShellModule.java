package org.mindis.gui.shell;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
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
    private final @Nullable String selectedIconLiteral;
    private final ReadOnlyIntegerWrapper badgeCount = new ReadOnlyIntegerWrapper(this, "badgeCount", 0);

    protected ShellModule(String name) {
        this(name, null, null);
    }

    /// @param iconLiteral Ikonli icon literal (e.g. `"mdi2v-view-dashboard"`);
    ///                    `null` for a text-only sidebar entry
    protected ShellModule(String name, @Nullable String iconLiteral) {
        this(name, iconLiteral, null);
    }

    /// @param iconLiteral         the entry's resting icon, conventionally the
    ///                            `-outline` variant
    /// @param selectedIconLiteral the icon while this module is the active one,
    ///                            conventionally the filled variant of the same
    ///                            glyph; `null` to keep [#getIconLiteral()]
    ///                            in both states
    protected ShellModule(String name, @Nullable String iconLiteral, @Nullable String selectedIconLiteral) {
        this.name = name;
        this.iconLiteral = iconLiteral;
        this.selectedIconLiteral = selectedIconLiteral;
    }

    /// A count worth seeing without opening the module - open slots, say. Zero
    /// hides it. The sidebar shows it as a pill beside the entry, and as a dot on
    /// the icon-only rail, where a number has nowhere to fit.
    public final ReadOnlyIntegerProperty badgeCountProperty() {
        return badgeCount.getReadOnlyProperty();
    }

    /// Modules that have something to report keep this up to date; the rest never
    /// touch it and show no badge.
    protected final void setBadgeCount(int count) {
        badgeCount.set(Math.max(0, count));
    }

    public final String getName() {
        return name;
    }

    public final @Nullable String getIconLiteral() {
        return iconLiteral;
    }

    /// The icon for the active entry - the filled counterpart of
    /// [#getIconLiteral()], or it again where a module declares only one.
    public final @Nullable String getSelectedIconLiteral() {
        return selectedIconLiteral == null ? iconLiteral : selectedIconLiteral;
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
