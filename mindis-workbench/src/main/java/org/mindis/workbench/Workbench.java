package org.mindis.workbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

/**
 * Minimal workbench shell: a home tab with one tile per module plus one
 * closable tab per open module. API modeled on WorkbenchFX (Apache-2.0),
 * implemented from scratch against JavaFX 26 and AtlantaFX styling
 * (see docs/adr/005-workbench-shell.md).
 */
public final class Workbench extends BorderPane {

    private final TabPane tabPane = new TabPane();
    private final Tab homeTab;
    private final List<WorkbenchModule> modules;
    private final Map<WorkbenchModule, Tab> openTabs = new LinkedHashMap<>();

    private WorkbenchModule activeModule;

    private Workbench(Builder builder) {
        this.modules = List.copyOf(builder.modules);
        getStyleClass().add("workbench");
        getStylesheets().add(Workbench.class.getResource("workbench.css").toExternalForm());

        homeTab = new Tab(builder.homeTabTitle);
        homeTab.setClosable(false);
        homeTab.setContent(createHomePage());

        tabPane.getTabs().add(homeTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> onTabSelected(newTab));

        setCenter(tabPane);
    }

    public static Builder builder(WorkbenchModule... modules) {
        return new Builder(modules);
    }

    public List<WorkbenchModule> getModules() {
        return modules;
    }

    public List<WorkbenchModule> getOpenModules() {
        return new ArrayList<>(openTabs.keySet());
    }

    /**
     * Opens the module in a new tab, or selects its tab if already open.
     */
    public void openModule(WorkbenchModule module) {
        Tab existing = openTabs.get(module);
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return;
        }
        Tab tab = new Tab(module.getName());
        tab.setOnCloseRequest(event -> {
            if (!module.destroy()) {
                event.consume();
            }
        });
        tab.setOnClosed(event -> {
            openTabs.remove(module);
            if (activeModule == module) {
                activeModule = null;
            }
        });
        openTabs.put(module, tab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    private void onTabSelected(Tab newTab) {
        if (activeModule != null) {
            activeModule.deactivate();
            activeModule = null;
        }
        if (newTab == null || newTab == homeTab) {
            return;
        }
        for (Map.Entry<WorkbenchModule, Tab> entry : openTabs.entrySet()) {
            if (entry.getValue() == newTab) {
                WorkbenchModule module = entry.getKey();
                newTab.setContent(module.activate());
                activeModule = module;
                return;
            }
        }
    }

    private Node createHomePage() {
        TilePane tiles = new TilePane();
        tiles.getStyleClass().add("workbench-home");
        tiles.setAlignment(Pos.CENTER);
        tiles.setHgap(16);
        tiles.setVgap(16);
        tiles.setPrefColumns(3);
        for (WorkbenchModule module : modules) {
            Button tile = new Button(module.getName());
            tile.getStyleClass().add("workbench-tile");
            tile.setOnAction(event -> openModule(module));
            tiles.getChildren().add(tile);
        }
        return tiles;
    }

    public static final class Builder {

        private final List<WorkbenchModule> modules;
        private String homeTabTitle = "Home";

        private Builder(WorkbenchModule... modules) {
            this.modules = List.of(modules);
        }

        public Builder homeTabTitle(String title) {
            this.homeTabTitle = title;
            return this;
        }

        public Workbench build() {
            return new Workbench(this);
        }
    }
}
