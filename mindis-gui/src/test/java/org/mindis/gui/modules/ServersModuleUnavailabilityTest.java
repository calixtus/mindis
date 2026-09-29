package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.PowerPane;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.FxTest;
import org.mindis.gui.data.LiveStore;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.shell.ShellOverlays;

/// Adding an unavailability period used to be a silent no-op whenever the two
/// pickers did not both hold a value - including the entirely reasonable "from
/// this day, one day only" input. It now either adds the period or says why it
/// did not.
class ServersModuleUnavailabilityTest {

    @TempDir
    Path tempDir;

    @Test
    void anEmptyEndDateAddsASingleDay() throws Exception {
        FxTest.runAndWait(() -> {
            Editor editor = openEditor();
            editor.from().setValue(LocalDate.of(2026, 3, 10));

            editor.add().fire();

            assertEquals(List.of(new UnavailabilityPeriod(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10))),
                    editor.periods().getItems());
            assertEquals(editor.periods().getItems(), editor.store().items().getFirst().unavailabilities(),
                    "the edit writes through to the live store");
            assertFalse(editor.error().isVisible(), "a period that was added needs no complaint");
        });
    }

    @Test
    void aMissingStartDateSaysSoInsteadOfDoingNothing() throws Exception {
        FxTest.runAndWait(() -> {
            Editor editor = openEditor();
            editor.to().setValue(LocalDate.of(2026, 3, 10));

            editor.add().fire();

            assertTrue(editor.periods().getItems().isEmpty());
            assertTrue(editor.error().isVisible(), "the user has to learn why nothing was added");
            assertFalse(editor.error().getText().isBlank());
        });
    }

    @Test
    void anEndBeforeTheStartSaysSoAndClearsOnCorrection() throws Exception {
        FxTest.runAndWait(() -> {
            Editor editor = openEditor();
            editor.from().setValue(LocalDate.of(2026, 3, 10));
            editor.to().setValue(LocalDate.of(2026, 3, 1));

            editor.add().fire();

            assertTrue(editor.periods().getItems().isEmpty());
            assertTrue(editor.error().isVisible());

            editor.to().setValue(LocalDate.of(2026, 3, 12));
            assertFalse(editor.error().isVisible(), "correcting the input clears the complaint");

            editor.add().fire();
            assertEquals(List.of(new UnavailabilityPeriod(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12))),
                    editor.periods().getItems());
        });
    }

    private Editor openEditor() {
        List<Server> staged = new ArrayList<>();
        staged.add(new Server("S1", "Anna", "Becker", "", null, null,
                Set.of(), Set.of(), List.of(), Set.of(), false, true));
        LiveStore<Server> serverStore = new LiveStore<>(
                () -> new ArrayList<>(staged),
                server -> {
                    staged.removeIf(candidate -> candidate.id().equals(server.id()));
                    staged.add(server);
                },
                server -> staged.removeIf(candidate -> candidate.id().equals(server.id())),
                Server::id, Objects::equals);
        LiveStore<Role> roleStore = new LiveStore<>(
                List::of, role -> { }, role -> { }, Role::id, Objects::equals);

        ServersModule module = new ServersModule("Servers", serverStore, roleStore,
                new ServerRepository(), new RoleRepository(),
                new UiPreferences(FxTest.preferencesAt(tempDir.resolve("preferences.json"))),
                new ShellOverlays(PowerPane::new));
        Node content = module.activate();
        FxTest.find(content, TableView.class).getSelectionModel().selectFirst();
        return findPeriodControls(FxTest.find(content, GridPane.class), serverStore);
    }

    /// The unavailability section is the one row built as a list, its controls
    /// and its message label in a box of their own - which is what identifies
    /// it here, rather than a position in the grid that any new field would
    /// shift.
    private static Editor findPeriodControls(GridPane grid, LiveStore<Server> store) {
        for (Node child : grid.getChildren()) {
            if (child instanceof VBox box
                    && box.getChildren().size() == 3
                    && box.getChildren().get(0) instanceof ListView<?> periods
                    && box.getChildren().get(1) instanceof FlowPane controls
                    && box.getChildren().get(2) instanceof Label error) {
                @SuppressWarnings("unchecked")
                ListView<UnavailabilityPeriod> typed = (ListView<UnavailabilityPeriod>) periods;
                return new Editor(typed,
                        (CalendarPicker) controls.getChildren().get(0),
                        (CalendarPicker) controls.getChildren().get(1),
                        (Button) controls.getChildren().get(2),
                        error, store);
            }
        }
        throw new AssertionError("no unavailability section in the Servers editor");
    }

    private record Editor(ListView<UnavailabilityPeriod> periods, CalendarPicker from, CalendarPicker to,
                          Button add, Label error, LiveStore<Server> store) {
    }
}
