package org.mindis.gui.shell;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// The sidebar's width model and the state that hangs off it.
///
/// Every number here is a constant private to [AppShell]; they are written out
/// because the point is the behaviour at the boundaries, which is where a width
/// model goes wrong: the rail width (60), the narrowest labelled width (200), the
/// widest (360), and the threshold below which a width collapses (120).
class AppShellSidebarTest {

    private static final class TestModule extends ShellModule {

        private TestModule(String name) {
            super(name, "mdi2c-church-outline", "mdi2c-church");
        }

        void badge(int count) {
            setBadgeCount(count);
        }

        @Override
        public Node activate() {
            return new Label(getName());
        }
    }

    private static AppShell shellAt(double width, ShellModule... modules) {
        return AppShell.builder(modules.length == 0 ? new ShellModule[] {new TestModule("Dashboard")} : modules)
                .initialSidebarWidth(width)
                .build();
    }

    private static List<Node> styled(AppShell shell, String styleClass) {
        return FxTest.findAll(shell.getLeft(), Node.class).stream()
                .filter(node -> node.getStyleClass().contains(styleClass))
                .toList();
    }

    @Test
    void aWidthInsideTheBandIsKept() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(220);
            assertAll(
                    () -> assertEquals(220, shell.getSidebarWidth()),
                    () -> assertFalse(shell.collapsedProperty().get()));
        });
    }

    @Test
    void tooNarrowToLabelButAboveTheThresholdWidensToTheMinimum() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(150);
            assertAll(
                    () -> assertEquals(200, shell.getSidebarWidth()),
                    () -> assertFalse(shell.collapsedProperty().get(), "150 is above the collapse threshold"));
        });
    }

    @Test
    void belowTheThresholdSnapsToTheRail() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(50);
            assertAll(
                    () -> assertEquals(60, shell.getSidebarWidth()),
                    () -> assertTrue(shell.collapsedProperty().get()));
        });
    }

    @Test
    void aStaleOrAbsurdWidthIsClampedRatherThanTrusted() throws InterruptedException {
        FxTest.runAndWait(() -> {
            assertEquals(360, shellAt(10_000).getSidebarWidth(), "a persisted width from another layout");
            assertEquals(60, shellAt(-1).getSidebarWidth(), "a nonsense width must still land somewhere sane");
        });
    }

    /// On the rail the entry is only an icon, so the name has to move to a tooltip
    /// or the module becomes unidentifiable.
    @Test
    void theRailHidesLabelsAndExplainsItselfWithTooltips() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(50, new TestModule("Dashboard"));
            ToggleButton entry = FxTest.findAll(shell.getLeft(), ToggleButton.class).getFirst();
            Label name = (Label) styled(shell, "shell-nav-name").getFirst();

            assertAll(
                    () -> assertFalse(name.isVisible(), "the label has no room on the rail"),
                    () -> assertFalse(name.isManaged(), "and must not take space either"),
                    () -> assertNotNull(entry.getTooltip(), "so the name has to be reachable somehow"),
                    () -> assertEquals("Dashboard", entry.getTooltip().getText()));
        });
    }

    @Test
    void alabelledSidebarNeedsNoTooltip() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(256, new TestModule("Dashboard"));
            ToggleButton entry = FxTest.findAll(shell.getLeft(), ToggleButton.class).getFirst();
            Label name = (Label) styled(shell, "shell-nav-name").getFirst();

            assertAll(
                    () -> assertTrue(name.isVisible()),
                    () -> assertEquals("Dashboard", name.getText()),
                    () -> assertNull(entry.getTooltip(), "the label is right there"));
        });
    }

    @Test
    void aCountShowsAsAPillWhileTheSidebarIsLabelled() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule services = new TestModule("Services");
            AppShell shell = shellAt(256, services);
            Label pill = (Label) styled(shell, "shell-nav-badge").getFirst();
            Region dot = (Region) styled(shell, "shell-nav-badge-dot").getFirst();

            assertFalse(pill.isVisible(), "nothing to report yet");

            services.badge(3);

            assertAll(
                    () -> assertTrue(pill.isVisible()),
                    () -> assertEquals("3", pill.getText()),
                    () -> assertFalse(dot.isVisible(), "the dot is the rail's stand-in, not a second badge"));
        });
    }

    /// A number has nowhere to fit at 60px, so the rail shows only that there is one.
    @Test
    void aCountShowsAsADotOnTheRail() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule services = new TestModule("Services");
            AppShell shell = shellAt(50, services);
            Label pill = (Label) styled(shell, "shell-nav-badge").getFirst();
            Region dot = (Region) styled(shell, "shell-nav-badge-dot").getFirst();

            services.badge(7);

            assertAll(
                    () -> assertTrue(dot.isVisible()),
                    () -> assertFalse(pill.isVisible(), "no room for the number here"));
        });
    }

    @Test
    void zeroShowsNothingAtAll() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule services = new TestModule("Services");
            AppShell shell = shellAt(256, services);
            services.badge(5);
            services.badge(0);

            assertAll(
                    () -> assertFalse(((Label) styled(shell, "shell-nav-badge").getFirst()).isVisible()),
                    () -> assertFalse(styled(shell, "shell-nav-badge-dot").getFirst().isVisible()));
        });
    }

    /// Expanding shows the labels before the glide has widened the sidebar onto them.
    @Test
    void theSidebarClipsWhatDoesNotFitItsCurrentWidth() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = shellAt(220);
            new Scene(shell, 800, 400);
            shell.applyCss();
            shell.layout();
            Region sidebar = (Region) styled(shell, "shell-sidebar").getFirst();

            Rectangle clip = (Rectangle) sidebar.getClip();

            assertAll(
                    () -> assertNotNull(clip),
                    () -> assertEquals(sidebar.getWidth(), clip.getWidth()),
                    () -> assertEquals(sidebar.getHeight(), clip.getHeight()));
        });
    }
}
