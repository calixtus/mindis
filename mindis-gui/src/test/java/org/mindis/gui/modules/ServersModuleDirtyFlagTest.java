package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.dlsc.gemsfx.PowerPane;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.FxTest;
import org.mindis.gui.data.LiveStore;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.shell.ShellOverlays;

/// The Servers editor is the largest of the four and the one the EditorForm
/// extraction rewrote most, so its unsaved-change accent is pinned here: it
/// appears on edit and clears on the next Save all, without the row being
/// reselected. Same contract [RolesModuleDirtyFlagTest] covers for the
/// simplest editor, over the one that also has collection-backed sections.
class ServersModuleDirtyFlagTest {

    @TempDir
    Path tempDir;

    @Test
    void dirtyAccentAppearsOnEditAndClearsAfterSaveAll() throws Exception {
        FxTest.runAndWait(() -> {
            List<Server> staged = new ArrayList<>();
            staged.add(new Server("S1", "Anna", "Becker", "", null, null,
                    Set.of(), List.of(), Set.of(), false, true));

            LiveStore<Server> serverStore = new LiveStore<>(
                    () -> new ArrayList<>(staged),
                    server -> upsert(staged, server),
                    server -> staged.removeIf(s -> s.id().equals(server.id())),
                    Server::id, Objects::equals);
            LiveStore<Role> roleStore = new LiveStore<>(
                    List::of, role -> { }, role -> { }, Role::id, Objects::equals);

            ServersModule module = new ServersModule("Servers", serverStore, roleStore,
                    new ServerRepository(), new RoleRepository(),
                    new UiPreferences(FxTest.preferencesAt(tempDir.resolve("preferences.json"))),
                    new ShellOverlays(PowerPane::new));
            Node content = module.activate();

            FxTest.find(content, TableView.class).getSelectionModel().selectFirst();

            GridPane grid = FxTest.find(content, GridPane.class);
            Label firstNameLabel = (Label) grid.getChildren().getFirst();
            TextField firstNameField = (TextField) grid.getChildren().get(1);

            assertFalse(firstNameLabel.getStyleClass().contains("field-changed"), "should start clean");

            firstNameField.setText("Anna Edited");
            assertTrue(firstNameLabel.getStyleClass().contains("field-changed"), "edit should mark dirty");

            // Simulate the global Save all: staged IS "disk" here, so flushing
            // is a no-op and only the re-baseline matters.
            serverStore.refresh();

            assertFalse(firstNameLabel.getStyleClass().contains("field-changed"),
                    "dirty accent should clear right after Save all, not require reselecting the row");
        });
    }

    private static void upsert(List<Server> staged, Server server) {
        staged.removeIf(s -> s.id().equals(server.id()));
        staged.add(server);
    }
}
