package org.mindis.gui.modules;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Subscription;

import org.jspecify.annotations.Nullable;

import org.mindis.core.overview.PlanOverview;
import org.mindis.gui.dashboard.DashboardView;
import org.mindis.gui.dashboard.DashboardViewModel;
import org.mindis.gui.shell.ShellModule;

/// Overview module. Content is rebuilt on every activation, and while the
/// module is showing it follows the live stores - another document opened
/// underneath it replaces the board instead of leaving the previous parish's
/// figures on screen.
public final class DashboardModule extends ShellModule {

    private final DashboardViewModel viewModel;
    private final StackPane host = new StackPane();

    private @Nullable Subscription storeSubscription;
    private @Nullable PlanOverview shown;
    private boolean refreshPending;

    public DashboardModule(String name, DashboardViewModel viewModel) {
        super(name, "mdi2v-view-dashboard-outline", "mdi2v-view-dashboard");
        this.viewModel = viewModel;
    }

    @Override
    public Node activate() {
        render(viewModel.loadOverview());
        if (storeSubscription == null) {
            storeSubscription = viewModel.subscribeToChanges(this::scheduleRefresh);
        }
        return host;
    }

    @Override
    public void deactivate() {
        unsubscribe();
    }

    @Override
    public void dispose() {
        unsubscribe();
    }

    /// Opening a document refreshes every store in turn; coalescing the burst
    /// into one later pass builds the board once instead of once per store.
    private void scheduleRefresh() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        Platform.runLater(() -> {
            refreshPending = false;
            if (storeSubscription == null) {
                return;
            }
            PlanOverview overview = viewModel.loadOverview();
            // A save re-baselines the stores without changing a value; rebuilding
            // then would only throw away the user's scroll position.
            if (!overview.equals(shown)) {
                render(overview);
            }
        });
    }

    private void render(PlanOverview overview) {
        shown = overview;
        host.getChildren().setAll(new DashboardView(viewModel, overview));
    }

    private void unsubscribe() {
        if (storeSubscription != null) {
            storeSubscription.unsubscribe();
            storeSubscription = null;
        }
    }
}
