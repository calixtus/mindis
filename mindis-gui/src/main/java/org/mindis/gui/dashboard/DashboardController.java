package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * Overview: upcoming services with their staffing state from the accepted
 * plan, unassigned-slot count, and per-server load.
 */
@Prototype
public class DashboardController {

    private final DashboardViewModel viewModel;

    @FXML
    private Label summaryLabel;
    @FXML
    private ListView<String> nextServicesList;
    @FXML
    private ListView<String> serverLoadList;

    // NullAway: @FXML fields are populated by FXMLLoader reflection right
    // after this constructor runs, before initialize() is called.
    @SuppressWarnings("NullAway.Init")
    public DashboardController(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        DashboardViewModel.Snapshot snapshot = viewModel.loadSnapshot();
        summaryLabel.setText(snapshot.summaryText());
        nextServicesList.setItems(FXCollections.observableArrayList(snapshot.upcomingServices()));
        serverLoadList.setItems(FXCollections.observableArrayList(snapshot.serverLoad()));
    }
}
