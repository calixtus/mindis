package org.mindis.gui.modules;

import javafx.scene.Node;

import org.mindis.gui.dashboard.DashboardView;
import org.mindis.gui.dashboard.DashboardViewModel;
import org.mindis.gui.shell.ShellModule;

/// Overview module. Content is rebuilt on every activation so the dashboard
/// always reflects the latest roster/services/plan state.
public final class DashboardModule extends ShellModule {

    private final DashboardViewModel viewModel;

    public DashboardModule(String name, DashboardViewModel viewModel) {
        super(name, "mdi2v-view-dashboard");
        this.viewModel = viewModel;
    }

    @Override
    public Node activate() {
        return new DashboardView(viewModel);
    }
}
