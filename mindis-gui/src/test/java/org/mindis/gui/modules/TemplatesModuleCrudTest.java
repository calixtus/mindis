package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.dlsc.gemsfx.PowerPane;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import org.kordamp.ikonli.javafx.FontIcon;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.gui.FxTest;
import org.mindis.gui.data.LiveStore;
import org.mindis.gui.shell.ShellOverlays;

/// Template CRUD driven through the screen's own controls, which PLAN.md M2 recorded
/// as covered by unit tests but "not yet via UI click-through" - it was waiting on a
/// headless toolkit.
///
/// Buttons are found by their Ikonli literal rather than their label, so the test does
/// not depend on which language the machine running it happens to be in.
class TemplatesModuleCrudTest {

    /// Stands in for the repository's cache: staging writes straight into it, the way a
    /// real repository's save/delete does, so an assertion sees what the solver or a CSV
    /// mapper reading the repository would see.
    private final List<ServiceTemplate> staged = new ArrayList<>();

    private LiveStore<ServiceTemplate> newStore() {
        return new LiveStore<>(
                () -> new ArrayList<>(staged),
                template -> {
                    staged.removeIf(existing -> existing.id().equals(template.id()));
                    staged.add(template);
                },
                template -> staged.removeIf(existing -> existing.id().equals(template.id())),
                ServiceTemplate::id,
                Objects::equals);
    }

    private TemplatesModule newModule(LiveStore<ServiceTemplate> store) {
        LiveStore<Role> roles = new LiveStore<>(
                ArrayList::new, role -> { }, role -> { }, Role::id, Objects::equals);
        return new TemplatesModule("Templates", store, roles, new RoleRepository(),
                new ShellOverlays(PowerPane::new));
    }

    private static Button toolbarButton(Node content, String iconLiteral) {
        return FxTest.findAll(content, Button.class).stream()
                .filter(button -> button.getGraphic() instanceof FontIcon icon
                        && iconLiteral.equals(icon.getIconLiteral()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no toolbar button with icon " + iconLiteral));
    }

    @Test
    void newStagesATemplateAndOpensItForEditing() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<ServiceTemplate> store = newStore();
            TemplatesModule module = newModule(store);
            Node content = module.activate();

            assertTrue(staged.isEmpty(), "nothing to begin with");

            toolbarButton(content, "mdi2p-plus").fire();

            TableView<?> table = FxTest.find(content, TableView.class);
            assertAll(
                    () -> assertEquals(1, staged.size(), "New writes through immediately"),
                    () -> assertEquals(1, store.items().size()),
                    () -> assertSame(store.items().getFirst(), table.getSelectionModel().getSelectedItem(),
                            "and selects the row it just made, so the editor is on it"),
                    () -> assertNotNull(FxTest.find(content, TextField.class),
                            "the editor is built for the new row"));
        });
    }

    @Test
    void editingAFieldWritesThroughToTheRepository() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<ServiceTemplate> store = newStore();
            TemplatesModule module = newModule(store);
            Node content = module.activate();
            toolbarButton(content, "mdi2p-plus").fire();

            // The one free-text field on this editor is the location.
            TextField location = FxTest.findAll(content, TextField.class).getFirst();
            location.setText("St. Mary");

            assertAll(
                    () -> assertEquals(1, staged.size(), "still one template, not a second one"),
                    () -> assertEquals("St. Mary", staged.getFirst().location()),
                    () -> assertEquals("St. Mary", store.items().getFirst().location(),
                            "the live row and the repository agree"));
        });
    }

    @Test
    void deleteRemovesTheSelectedTemplate() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<ServiceTemplate> store = newStore();
            TemplatesModule module = newModule(store);
            Node content = module.activate();
            toolbarButton(content, "mdi2p-plus").fire();
            assertEquals(1, staged.size());

            toolbarButton(content, "mdi2d-delete").fire();

            assertAll(
                    () -> assertTrue(staged.isEmpty(), "gone from the repository too"),
                    () -> assertTrue(store.items().isEmpty()));
        });
    }

    /// Nothing selected means nothing to delete - the button is bound to the selection
    /// rather than left live to act on whatever happens to be first.
    @Test
    void deleteIsDisabledWithNoSelection() throws InterruptedException {
        FxTest.runAndWait(() -> {
            TemplatesModule module = newModule(newStore());
            Node content = module.activate();

            assertTrue(toolbarButton(content, "mdi2d-delete").isDisabled());

            toolbarButton(content, "mdi2p-plus").fire();

            assertFalse(toolbarButton(content, "mdi2d-delete").isDisabled(),
                    "enabled once there is a row to act on");
        });
    }

    @Test
    void severalTemplatesCanBeAddedAndTheNewestComesFirst() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<ServiceTemplate> store = newStore();
            TemplatesModule module = newModule(store);
            Node content = module.activate();
            Button add = toolbarButton(content, "mdi2p-plus");

            add.fire();
            String first = store.items().getFirst().id();
            add.fire();
            String second = store.items().getFirst().id();

            assertAll(
                    () -> assertEquals(2, staged.size()),
                    () -> assertNotEquals(first, second, "each New makes its own row"),
                    () -> assertEquals(second, store.items().getFirst().id(),
                            "insertFirst puts the newest at the top, where the user is looking"));
        });
    }
}
