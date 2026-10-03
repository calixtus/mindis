package org.mindis.gui.modules;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.gui.FxTest;
import org.mindis.gui.TestPreferences;
import org.mindis.gui.TestStores;
import org.mindis.gui.dashboard.DashboardViewModel;
import org.mindis.gui.data.LiveStore;

/// The board follows the live stores while it is showing - another document
/// opened underneath it must not leave the previous parish on screen - but a
/// save, which changes no value, must not rebuild it.
///
/// Each step is its own [FxTest#runAndWait] because the module coalesces store
/// changes into a `Platform.runLater` pass, which runs before the next step.
class DashboardModuleTest {

    @TempDir
    Path tempDir;

    private final ServiceRepository services = new ServiceRepository();
    private final LiveStore<LiturgicalService> serviceStore = TestStores.services(services);
    private final AtomicReference<Node> content = new AtomicReference<>();

    private DashboardModule module() {
        return new DashboardModule("Dashboard", new DashboardViewModel(serviceStore,
                TestStores.servers(new ServerRepository()), TestStores.roles(new RoleRepository()),
                new ArchivedServiceRepository(), TestPreferences.at(tempDir.resolve("preferences.json"))));
    }

    private static LiturgicalService upcomingService() {
        return new LiturgicalService(LiturgicalService.newId(), LocalDateTime.now().plusDays(3), 60, "St. Mary",
                ServiceType.SUNDAY_MASS, "", List.of(new Slot(Slot.newId(), "ACOLYTE", null, false)), "");
    }

    private static Node board(Node host) {
        return ((Pane) host).getChildren().getFirst();
    }

    @Test
    void aChangedStoreRebuildsTheBoardWhileItIsShowing() throws InterruptedException {
        DashboardModule module = module();
        AtomicReference<Node> host = new AtomicReference<>();
        FxTest.runAndWait(() -> {
            host.set(module.activate());
            content.set(board(host.get()));
            serviceStore.insertFirst(upcomingService());
        });
        FxTest.runAndWait(() -> assertNotSame(content.get(), board(host.get())));
    }

    @Test
    void aRefreshThatChangesNothingKeepsTheBoard() throws InterruptedException {
        services.save(upcomingService());
        serviceStore.refresh();
        DashboardModule module = module();
        AtomicReference<Node> host = new AtomicReference<>();
        FxTest.runAndWait(() -> {
            host.set(module.activate());
            content.set(board(host.get()));
            serviceStore.refresh();
        });
        FxTest.runAndWait(() -> assertSame(content.get(), board(host.get())));
    }

    @Test
    void aHiddenBoardIgnoresChanges() throws InterruptedException {
        DashboardModule module = module();
        AtomicReference<Node> host = new AtomicReference<>();
        FxTest.runAndWait(() -> {
            host.set(module.activate());
            content.set(board(host.get()));
            module.deactivate();
            serviceStore.insertFirst(upcomingService());
        });
        FxTest.runAndWait(() -> assertSame(content.get(), board(host.get())));
    }
}
