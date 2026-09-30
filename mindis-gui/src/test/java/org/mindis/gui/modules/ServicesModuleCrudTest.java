package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.dlsc.gemsfx.PowerPane;

import javafx.scene.Node;
import javafx.scene.control.Button;

import org.kordamp.ikonli.javafx.FontIcon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.ArchiveService;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.core.export.PlanExportService;
import org.mindis.gui.FxTest;
import org.mindis.gui.data.LiveStore;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.shell.ShellOverlays;

/// Service CRUD driven through the screen's own toolbar, the other half of the UI
/// click-through PLAN.md M2 left open. Buttons are found by Ikonli literal, so the test
/// does not depend on the machine's language.
// NullAway: @TempDir is injected after construction, so the collaborators built from it
// cannot be field initializers.
@SuppressWarnings("NullAway.Init")
class ServicesModuleCrudTest {

    @TempDir
    Path tempDir;

    private final List<LiturgicalService> staged = new ArrayList<>();
    private final ServerRepository servers = new ServerRepository();
    private final ServiceRepository services = new ServiceRepository();
    private final RoleRepository roles = new RoleRepository();
    private final ArchivedServiceRepository archived = new ArchivedServiceRepository();
    private PlanningService planningService;

    @AfterEach
    void closeSolver() {
        if (planningService != null) {
            planningService.close();
        }
    }

    private LiveStore<LiturgicalService> newStore() {
        return new LiveStore<>(
                () -> new ArrayList<>(staged),
                service -> {
                    staged.removeIf(existing -> existing.id().equals(service.id()));
                    staged.add(service);
                },
                service -> staged.removeIf(existing -> existing.id().equals(service.id())),
                LiturgicalService::id,
                Objects::equals);
    }

    private ServicesModule newModule(LiveStore<LiturgicalService> store) {
        PreferencesService preferences = FxTest.preferencesAt(tempDir.resolve("preferences.json"));
        ArchiveService archiveService = new ArchiveService(roles, servers, services, archived);
        planningService = new PlanningService(servers, services, roles, preferences, archiveService);
        AppDatabase database = new AppDatabase(roles, servers, new TemplateRepository(), services, archived);
        PlanningViewModel planningViewModel = new PlanningViewModel(planningService, preferences,
                new PlanExportService(servers, roles, database, new DataDirectory(tempDir)), archiveService);

        LiveStore<Role> roleStore = new LiveStore<>(
                ArrayList::new, role -> { }, role -> { }, Role::id, Objects::equals);
        LiveStore<Server> serverStore = new LiveStore<>(
                ArrayList::new, server -> { }, server -> { }, Server::id, Objects::equals);

        return new ServicesModule("Services", store, roleStore, serverStore,
                new TemplateRepository(), roles, planningViewModel,
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
    void newStagesAService() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<LiturgicalService> store = newStore();
            Node content = newModule(store).activate();

            assertTrue(staged.isEmpty());

            toolbarButton(content, "mdi2p-plus").fire();

            assertAll(
                    () -> assertEquals(1, staged.size(), "New writes through immediately"),
                    () -> assertEquals(1, store.items().size()));
        });
    }

    @Test
    void deleteRemovesTheSelectedService() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<LiturgicalService> store = newStore();
            Node content = newModule(store).activate();
            toolbarButton(content, "mdi2p-plus").fire();

            toolbarButton(content, "mdi2d-delete").fire();

            assertAll(
                    () -> assertTrue(staged.isEmpty(), "gone from the repository too"),
                    () -> assertTrue(store.items().isEmpty()));
        });
    }

    @Test
    void deleteIsDisabledWithNoSelection() throws InterruptedException {
        FxTest.runAndWait(() -> {
            Node content = newModule(newStore()).activate();

            assertTrue(toolbarButton(content, "mdi2d-delete").isDisabled());

            toolbarButton(content, "mdi2p-plus").fire();

            assertFalse(toolbarButton(content, "mdi2d-delete").isDisabled());
        });
    }

    /// The badge on the sidebar entry counts open slots, and a service created from the
    /// toolbar starts with none - so adding one must not make the badge claim work that
    /// does not exist yet.
    @Test
    void aFreshServiceAddsNothingToTheOpenSlotBadge() throws InterruptedException {
        FxTest.runAndWait(() -> {
            LiveStore<LiturgicalService> store = newStore();
            ServicesModule module = newModule(store);
            Node content = module.activate();

            toolbarButton(content, "mdi2p-plus").fire();

            assertEquals(0, module.badgeCountProperty().get(),
                    "a service with no slots has nothing open");
        });
    }
}
