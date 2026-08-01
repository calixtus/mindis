package org.mindis.gui.shell;

import java.time.ZonedDateTime;
import java.util.function.Supplier;

import com.dlsc.gemsfx.DialogPane;
import com.dlsc.gemsfx.PowerPane;
import com.dlsc.gemsfx.infocenter.InfoCenterView;
import com.dlsc.gemsfx.infocenter.Notification;
import com.dlsc.gemsfx.infocenter.NotificationGroup;

/// The overlay layers the [PowerPane] wrapping the [AppShell] provides -
/// modal dialogs, transient notifications and the bottom drawer.
///
/// Constructed once in the composition root ([org.mindis.gui.MinDisApp]) and
/// handed to whoever needs it, like every other collaborator: an instance, not
/// static methods reaching for an ambient pane. That keeps callers honest about
/// their dependency.
///
/// The pane arrives as a [Supplier] and is never touched at construction, for
/// two reasons: the composition root is then free to build the shell before the
/// pane that wraps it, and a test for something that merely *holds* an overlay
/// reference does not have to boot the JavaFX toolkit to construct one.
///
/// Outlives a language rebuild, which swaps only the [PowerPane]'s content, so
/// a holder may keep the reference it was given.
public final class ShellOverlays {

    /// Notification group used when a caller does not name one; grouping only
    /// affects how the info center stacks entries, not their appearance.
    public static final String DEFAULT_GROUP = "MinDis";

    private final Supplier<PowerPane> powerPane;

    public ShellOverlays(Supplier<PowerPane> powerPane) {
        this.powerPane = powerPane;
    }

    /// The modal dialog layer. Prefer this over `new Alert(...)`: it renders
    /// inside the window with the app's own styling instead of opening a
    /// separate Modena-styled stage.
    ///
    /// `overlays.dialogs().showConfirmation(title, message).onClose(...)`
    public DialogPane dialogs() {
        return powerPane.get().getDialogPane();
    }

    /// Posts a transient notification into the info center. Adding it makes
    /// the info center slide in on its own and hide again after its
    /// `autoHideDuration`, so this is the non-blocking counterpart to
    /// [#dialogs()]: use it for "saved", "12 of 14 rows imported" and similar
    /// outcomes that need no answer.
    public void notify(String title, String summary) {
        notify(DEFAULT_GROUP, title, summary);
    }

    /// As [#notify(String, String)], into a named group.
    public void notify(String groupName, String title, String summary) {
        InfoCenterView view = powerPane.get().getInfoCenterPane().getInfoCenterView();
        group(view, groupName).getNotifications()
                .add(new Notification<>(title, summary, ZonedDateTime.now()));
    }

    /// The group named `groupName` in `view`, created and registered on first
    /// use. Groups are keyed by name (not held in a field) so this stays
    /// correct across a UI rebuild, which replaces the shell but keeps the
    /// same [PowerPane] and therefore the same info center.
    private static NotificationGroup<Object, Notification<Object>> group(InfoCenterView view, String groupName) {
        for (NotificationGroup<?, ?> existing : view.getGroups()) {
            if (groupName.equals(existing.getName())) {
                // Safe: this class only ever registers groups of this exact
                // type, and only under a name it created.
                @SuppressWarnings("unchecked")
                NotificationGroup<Object, Notification<Object>> found =
                        (NotificationGroup<Object, Notification<Object>>) existing;
                return found;
            }
        }
        NotificationGroup<Object, Notification<Object>> created = new NotificationGroup<>(groupName);
        view.getGroups().add(created);
        return created;
    }
}
