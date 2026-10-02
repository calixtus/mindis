package org.mindis.gui.shell;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;

/// Smoke tests for the shell's own behaviour: navigating between modules, the
/// collapse/expand width model, and the badge. PLAN.md M1 listed these as blocked
/// on a headless toolkit; JavaFX's headless platform unblocked them.
///
/// They drive the real controls rather than the fields behind them, because the
/// bugs worth catching here have all been in the wiring - a nav button that could
/// deselect itself, a label left visible on the rail, a width that did not snap.
class AppShellNavigationTest {

    private static final PseudoClass KEYBOARD_FOCUS = PseudoClass.getPseudoClass("keyboard-focus");

    private static final class TestModule extends ShellModule {

        private int activations;

        private TestModule(String name) {
            super(name, "mdi2c-church-outline", "mdi2c-church");
        }

        void badge(int count) {
            setBadgeCount(count);
        }

        @Override
        public Node activate() {
            activations++;
            return new Label(getName() + " content " + activations);
        }
    }

    private static Node content(AppShell shell) {
        return ((StackPane) shell.getCenter()).getChildren().getFirst();
    }

    /// The nav buttons, which are every ToggleButton in the sidebar.
    private static List<ToggleButton> navButtons(AppShell shell) {
        return FxTest.findAll(shell.getLeft(), ToggleButton.class);
    }

    @Test
    void theFirstModuleIsActiveWhenTheShellOpens() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new TestModule("Servers")).build();

            assertAll(
                    () -> assertSame(dashboard, shell.getActiveModule()),
                    () -> assertEquals(1, dashboard.activations),
                    () -> assertNotNull(content(shell)));
        });
    }

    @Test
    void everyModuleCanBeOpened() throws InterruptedException {
        FxTest.runAndWait(() -> {
            List<TestModule> modules = List.of(
                    new TestModule("Dashboard"), new TestModule("Roles"), new TestModule("Servers"));
            AppShell shell = AppShell.builder(modules.toArray(ShellModule[]::new)).build();

            for (TestModule module : modules) {
                Node before = content(shell);
                shell.openModule(module);
                assertSame(module, shell.getActiveModule(), module.getName() + " should be active");
                if (module != modules.getFirst()) {
                    assertNotSame(before, content(shell), "content should follow the selection");
                }
            }
        });
    }

    @Test
    void bottomPinnedModulesAreReachableToo() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule settings = new TestModule("Settings");
            AppShell shell = AppShell.builder(new TestModule("Dashboard"))
                    .bottomModule(new TestModule("About"))
                    .bottomModule(settings)
                    .build();

            shell.openModule(settings);

            assertAll(
                    () -> assertSame(settings, shell.getActiveModule()),
                    () -> assertEquals(3, shell.getModules().size(), "getModules covers both groups"),
                    () -> assertEquals(3, navButtons(shell).size()));
        });
    }

    /// Clicking the active entry again would otherwise deselect it and leave the
    /// shell with no module at all - a ToggleGroup allows that by default.
    @Test
    void theActiveModuleCannotBeDeselected() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new TestModule("Servers")).build();
            ToggleButton active = navButtons(shell).getFirst();

            active.setSelected(false);

            assertAll(
                    () -> assertTrue(active.isSelected(), "the entry must select itself again"),
                    () -> assertSame(dashboard, shell.getActiveModule()));
        });
    }

    /// How the active module survives a language change: the shell is rebuilt with
    /// new module instances, so it is re-selected by class name, not by identity or
    /// by its localized name.
    @Test
    void aModuleCanBeReopenedByClassName() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            TestModule servers = new TestModule("Servers");
            AppShell shell = AppShell.builder(dashboard, servers).build();

            String active = shell.getActiveModuleClassName();
            shell.openModule(servers);
            shell.openModule(active);

            assertSame(dashboard, shell.getActiveModule());
        });
    }

    @Test
    void reopeningNothingLeavesTheSelectionAlone() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new TestModule("Servers")).build();

            shell.openModule((String) null);

            assertSame(dashboard, shell.getActiveModule());
        });
    }

    /// A scene with skins applied: without one the module list's `ScrollPane` has no
    /// skin, its content no parent, and key events never reach the sidebar.
    private static Scene laidOut(AppShell shell) {
        Scene scene = new Scene(shell);
        shell.applyCss();
        shell.layout();
        return scene;
    }

    private static void press(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /// `ToggleButton`'s own arrow handling selects the neighbour, which would build
    /// every module the user merely arrows past.
    @Test
    void arrowKeysMoveFocusWithoutOpeningAnything() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            TestModule servers = new TestModule("Servers");
            AppShell shell = AppShell.builder(dashboard, servers).build();
            Scene scene = laidOut(shell);
            List<ToggleButton> buttons = navButtons(shell);
            buttons.getFirst().requestFocus();

            press(buttons.getFirst(), KeyCode.DOWN);

            assertAll(
                    () -> assertSame(buttons.get(1), scene.getFocusOwner()),
                    () -> assertTrue(buttons.get(1).getPseudoClassStates().contains(KEYBOARD_FOCUS),
                            "requestFocus clears :focus-visible, so the ring needs its own state"),
                    () -> assertSame(dashboard, shell.getActiveModule()),
                    () -> assertEquals(0, servers.activations));
        });
    }

    @Test
    void arrowKeysWrapAcrossTopAndBottomEntries() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = AppShell.builder(new TestModule("Dashboard"), new TestModule("Servers"))
                    .bottomModule(new TestModule("Settings"))
                    .build();
            Scene scene = laidOut(shell);
            List<ToggleButton> buttons = navButtons(shell);
            buttons.getFirst().requestFocus();

            press(buttons.getFirst(), KeyCode.UP);
            Node wrappedToLast = scene.getFocusOwner();
            press(buttons.getLast(), KeyCode.DOWN);

            assertAll(
                    () -> assertSame(buttons.getLast(), wrappedToLast),
                    () -> assertSame(buttons.getFirst(), scene.getFocusOwner()));
        });
    }

    @Test
    void enterOpensTheFocusedEntry() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule servers = new TestModule("Servers");
            AppShell shell = AppShell.builder(new TestModule("Dashboard"), servers).build();
            laidOut(shell);
            ToggleButton serversButton = navButtons(shell).get(1);
            serversButton.requestFocus();

            press(serversButton, KeyCode.ENTER);

            assertSame(servers, shell.getActiveModule());
        });
    }

    @Test
    void leftAndRightDoNotSwitchModules() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new TestModule("Servers")).build();
            laidOut(shell);
            ToggleButton first = navButtons(shell).getFirst();
            first.requestFocus();

            press(first, KeyCode.RIGHT);
            press(first, KeyCode.LEFT);

            assertSame(dashboard, shell.getActiveModule());
        });
    }

    /// Tab reaches the list once, on the active entry, instead of stopping at every module.
    @Test
    void onlyTheActiveEntryIsATabStop() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule servers = new TestModule("Servers");
            AppShell shell = AppShell.builder(new TestModule("Dashboard"), servers).build();
            List<ToggleButton> buttons = navButtons(shell);

            shell.openModule(servers);

            assertAll(
                    () -> assertFalse(buttons.getFirst().isFocusTraversable()),
                    () -> assertTrue(buttons.get(1).isFocusTraversable()));
        });
    }

    @Test
    void homeAndEndJumpToTheEnds() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TestModule dashboard = new TestModule("Dashboard");
            AppShell shell = AppShell.builder(dashboard, new TestModule("Roles"), new TestModule("Servers"))
                    .bottomModule(new TestModule("Settings"))
                    .build();
            Scene scene = laidOut(shell);
            List<ToggleButton> buttons = navButtons(shell);
            buttons.get(1).requestFocus();

            press(buttons.get(1), KeyCode.END);
            Node atEnd = scene.getFocusOwner();
            press(buttons.getLast(), KeyCode.HOME);

            assertAll(
                    () -> assertSame(buttons.getLast(), atEnd),
                    () -> assertSame(buttons.getFirst(), scene.getFocusOwner()),
                    () -> assertSame(dashboard, shell.getActiveModule(), "jumping opens nothing"));
        });
    }

    /// `openModule` may come from anywhere in the app, with the entry scrolled out of the list.
    @Test
    void openingAModuleScrollsItsEntryIntoView() throws InterruptedException {
        FxTest.runAndWait(() -> {
            List<TestModule> modules = IntStream.range(0, 12)
                    .mapToObj(i -> new TestModule("Module " + i))
                    .toList();
            AppShell shell = AppShell.builder(modules.toArray(ShellModule[]::new)).build();
            new Scene(shell, 800, 200);
            shell.applyCss();
            shell.layout();
            ScrollPane navScroll = FxTest.find(shell.getLeft(), ScrollPane.class);

            shell.openModule(modules.getLast());

            assertEquals(1.0, navScroll.getVvalue(), 1e-9, "the last entry needs the list scrolled to its end");
        });
    }

    @Test
    void theKeyboardFocusMarkGoesWithTheFocus() throws InterruptedException {
        FxTest.runAndWait(() -> {
            AppShell shell = AppShell.builder(new TestModule("Dashboard"), new TestModule("Servers")).build();
            laidOut(shell);
            List<ToggleButton> buttons = navButtons(shell);
            buttons.getFirst().requestFocus();

            press(buttons.getFirst(), KeyCode.DOWN);
            buttons.getFirst().requestFocus();

            assertFalse(buttons.get(1).getPseudoClassStates().contains(KEYBOARD_FOCUS));
        });
    }
}
