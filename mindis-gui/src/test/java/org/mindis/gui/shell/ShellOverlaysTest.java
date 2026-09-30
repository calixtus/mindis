package org.mindis.gui.shell;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.dlsc.gemsfx.PowerPane;
import com.dlsc.gemsfx.infocenter.NotificationGroup;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// The overlay layers over the shell: the modal dialog pane and the info centre
/// notifications. PLAN.md M1 listed these as blocked on a headless toolkit.
class ShellOverlaysTest {

    private static List<NotificationGroup<?, ?>> groups(PowerPane pane) {
        return List.copyOf(pane.getInfoCenterPane().getInfoCenterView().getGroups());
    }

    private static NotificationGroup<?, ?> group(PowerPane pane, String name) {
        return groups(pane).stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no notification group named " + name));
    }

    @Test
    void dialogsAreTheWrappingPanesOwnDialogLayer() throws InterruptedException {
        FxTest.runAndWait(() -> {
            PowerPane pane = new PowerPane();
            ShellOverlays overlays = new ShellOverlays(() -> pane);

            assertSame(pane.getDialogPane(), overlays.dialogs(),
                    "dialogs must open inside the window, not in a stage of their own");
        });
    }

    @Test
    void aNotificationLandsInTheDefaultGroup() throws InterruptedException {
        FxTest.runAndWait(() -> {
            PowerPane pane = new PowerPane();
            new ShellOverlays(() -> pane).notify("Saved", "parish.mindis");

            assertAll(
                    () -> assertEquals(1, groups(pane).size()),
                    () -> assertEquals(ShellOverlays.DEFAULT_GROUP, groups(pane).getFirst().getName()),
                    () -> assertEquals(1, group(pane, ShellOverlays.DEFAULT_GROUP).getNotifications().size()));
        });
    }

    /// The group is looked up by name on every call rather than cached in a field,
    /// so that it survives a language rebuild - which replaces the shell but keeps
    /// this same PowerPane, and therefore the same info centre.
    @Test
    void aSecondNotificationReusesTheGroupRatherThanAddingOne() throws InterruptedException {
        FxTest.runAndWait(() -> {
            PowerPane pane = new PowerPane();
            ShellOverlays overlays = new ShellOverlays(() -> pane);

            overlays.notify("Saved", "parish.mindis");
            overlays.notify("Exported", "plan.pdf");

            assertAll(
                    () -> assertEquals(1, groups(pane).size(), "one group, not one per notification"),
                    () -> assertEquals(2, group(pane, ShellOverlays.DEFAULT_GROUP).getNotifications().size()));
        });
    }

    @Test
    void aNamedGroupIsKeptSeparate() throws InterruptedException {
        FxTest.runAndWait(() -> {
            PowerPane pane = new PowerPane();
            ShellOverlays overlays = new ShellOverlays(() -> pane);

            overlays.notify("Saved", "parish.mindis");
            overlays.notify("Import", "12 of 14 rows imported", "");

            assertAll(
                    () -> assertEquals(2, groups(pane).size()),
                    () -> assertEquals(1, group(pane, ShellOverlays.DEFAULT_GROUP).getNotifications().size()),
                    () -> assertEquals(1, group(pane, "Import").getNotifications().size()));
        });
    }

    /// Needs no toolkit, and that is the point: the pane arrives as a supplier that
    /// is never called at construction, so the composition root can build the shell
    /// before the pane that wraps it - and a holder of an overlays reference costs a
    /// test nothing.
    @Test
    void constructionNeverTouchesThePane() {
        ShellOverlays overlays = new ShellOverlays(() -> {
            throw new AssertionError("the pane must not be resolved until an overlay is used");
        });

        assertThrows(AssertionError.class, overlays::dialogs, "...and is resolved when one is");
    }
}
