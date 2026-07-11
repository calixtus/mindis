package org.mindis.gui.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

import org.junit.jupiter.api.Test;

import org.mindis.core.model.Role;
import org.mindis.core.persistence.RoleCsvMapper;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.workbench.LiveStore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Reproduces the reported bug: after editing a Role's name and clicking
/// "Save all", the name field's dirty accent (the "field-changed" style
/// class on its label) should clear immediately, without needing to switch
/// away and back.
class RolesModuleDirtyFlagTest {

    @Test
    void dirtyAccentClearsRightAfterSaveAll() throws Exception {
        runOnFxThreadAndWait(() -> {
            List<Role> staged = new ArrayList<>();
            staged.add(new Role("R1", "Acolyte", null, null, 0));

            LiveStore<Role> store = new LiveStore<>(
                    () -> new ArrayList<>(staged),
                    role -> upsert(staged, role),
                    role -> staged.removeIf(r -> r.id().equals(role.id())),
                    Role::id, Objects::equals);

            RolesModule module = new RolesModule("Roles", store, dummyRoleRepository());
            Node content = module.activate();

            TableView<Role> table = find(content, TableView.class);
            table.getSelectionModel().selectFirst();

            GridPane grid = find(content, GridPane.class);
            TextField nameField = (TextField) grid.getChildren().get(1);
            Label nameLabel = (Label) grid.getChildren().get(0);

            assertFalse(nameLabel.getStyleClass().contains("field-changed"), "should start clean");

            nameField.setText("Acolyte Edited");
            assertTrue(nameLabel.getStyleClass().contains("field-changed"), "edit should mark dirty");

            // Simulate the global Save all: flush (a no-op here, staged IS
            // "disk") then re-baseline every store, exactly like
            // LiveDatabase#saveAll().
            store.refresh();

            assertFalse(nameLabel.getStyleClass().contains("field-changed"),
                    "dirty accent should clear right after Save all, not require reselecting the row");
        });
    }

    private static void upsert(List<Role> staged, Role role) {
        staged.removeIf(r -> r.id().equals(role.id()));
        staged.add(role);
    }

    private static RoleRepository dummyRoleRepository() {
        // buildEditor doesn't touch the repository directly, only the CSV
        // mapper wiring in the constructor needs a non-null instance.
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T find(Node root, Class<T> type) {
        if (type.isInstance(root)) {
            return (T) root;
        }
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        if (root instanceof javafx.scene.control.ScrollPane scrollPane) {
            return find(scrollPane.getContent(), type);
        }
        if (root instanceof javafx.scene.control.SplitPane splitPane) {
            for (Node child : splitPane.getItems()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void runOnFxThreadAndWait(Runnable body) throws Exception {
        new JFXPanel(); // initializes the JavaFX toolkit
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
