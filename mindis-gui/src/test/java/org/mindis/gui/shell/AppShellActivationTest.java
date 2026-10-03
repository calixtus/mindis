package org.mindis.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.Node;
import javafx.scene.control.Label;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// When the shell asks a module for its content. A module that must follow data
/// changing underneath it while selected (the dashboard) subscribes to that data
/// itself; the shell activates a module only when the selection changes.
class AppShellActivationTest {

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
    void switchingModulesActivatesTheNewOne() throws InterruptedException {
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
