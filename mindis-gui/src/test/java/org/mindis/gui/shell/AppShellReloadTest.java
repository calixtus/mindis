package org.mindis.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// Rebuilding the active module's content in place.
///
/// The case this exists for: another document is opened while the dashboard is
/// showing. The dashboard reads its figures from the repositories as it builds
/// itself, so the numbers on screen describe the previous parish, and selecting
/// the module is the only thing that ever rebuilt it - which the user cannot do
/// for a module that is already selected.
class AppShellReloadTest {

    /// Counts how often the shell asked for content, and hands back a different
    /// node every time so a rebuild can be told from a reuse.
    private static final class CountingModule extends ShellModule {

        private int activations;

        private CountingModule(String name) {
            super(name);
        }

        @Override
        public Node activate() {
            activations++;
            return new Label(getName() + " " + activations);
        }
    }

    private static Node content(AppShell shell) {
        return ((StackPane) ((BorderPane) shell).getCenter()).getChildren().getFirst();
    }

    @Test
    void reloadRebuildsTheActiveModulesContent() throws InterruptedException {
        FxTest.runAndWait(() -> {
            CountingModule dashboard = new CountingModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new CountingModule("Servers")).build();

            assertEquals(1, dashboard.activations, "the first module is activated when the shell is built");
            Node before = content(shell);

            shell.reloadActiveModule();

            assertEquals(2, dashboard.activations);
            assertNotSame(before, content(shell), "the stale content must actually be replaced");
        });
    }

    @Test
    void reloadLeavesTheSelectionAlone() throws InterruptedException {
        FxTest.runAndWait(() -> {
            CountingModule dashboard = new CountingModule("Dashboard");
            CountingModule servers = new CountingModule("Servers");
            AppShell shell = AppShell.builder(dashboard, servers).build();

            shell.reloadActiveModule();

            assertSame(dashboard, shell.getActiveModule());
            assertEquals(0, servers.activations, "reloading must not wake the other modules");
        });
    }

    /// Selecting the module that is already selected is not a reload - that guard
    /// is what makes navigating away and back the only rebuild today, and it stays.
    @Test
    void reselectingTheActiveModuleDoesNotRebuild() throws InterruptedException {
        FxTest.runAndWait(() -> {
            CountingModule dashboard = new CountingModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new CountingModule("Servers")).build();

            shell.openModule(dashboard);

            assertEquals(1, dashboard.activations);
        });
    }

    @Test
    void switchingModulesStillActivatesTheNewOne() throws InterruptedException {
        FxTest.runAndWait(() -> {
            CountingModule dashboard = new CountingModule("Dashboard");
            CountingModule servers = new CountingModule("Servers");
            AppShell shell = AppShell.builder(dashboard, servers).build();

            shell.openModule(servers);

            assertEquals(1, servers.activations);
            assertSame(servers, shell.getActiveModule());
        });
    }
}
