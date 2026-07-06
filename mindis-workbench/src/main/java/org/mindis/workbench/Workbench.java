package org.mindis.workbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Minimal workbench shell: a permanent left sidebar with one navigation entry
 * per module (bottom-pinned entries supported, e.g. Settings) and the active
 * module's content on the right. API modeled on WorkbenchFX (Apache-2.0),
 * implemented from scratch against JavaFX 26 and AtlantaFX styling
 * (see docs/adr/005-workbench-shell.md).
 */
public final class Workbench extends BorderPane {

    private final Map<WorkbenchModule, ToggleButton> navButtons = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane contentPane = new StackPane();
    private final List<WorkbenchModule> modules;

    private WorkbenchModule activeModule;

    private Workbench(Builder builder) {
        this.modules = List.copyOf(builder.modules);
        getStyleClass().add("workbench");
        getStylesheets().add(Workbench.class.getResource("workbench.css").toExternalForm());

        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("workbench-sidebar");
        for (WorkbenchModule module : modules) {
            sidebar.getChildren().add(createNavButton(module));
        }
        if (!builder.bottomModules.isEmpty()) {
            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            sidebar.getChildren().add(spacer);
            for (WorkbenchModule module : builder.bottomModules) {
                sidebar.getChildren().add(createNavButton(module));
            }
        }

        // A ToggleGroup allows deselecting by re-clicking; keep one module
        // active at all times instead.
        navGroup.selectedToggleProperty().subscribe((oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                navGroup.selectToggle(oldToggle);
            }
        });

        setLeft(sidebar);
        setCenter(contentPane);

        if (!navButtons.isEmpty()) {
            navButtons.values().iterator().next().setSelected(true);
        }
    }

    public static Builder builder(WorkbenchModule... modules) {
        return new Builder(modules);
    }

    public List<WorkbenchModule> getModules() {
        return modules;
    }

    public WorkbenchModule getActiveModule() {
        return activeModule;
    }

    /**
     * Selects the module in the sidebar (activating it).
     */
    public void openModule(WorkbenchModule module) {
        ToggleButton button = navButtons.get(module);
        if (button != null) {
            button.setSelected(true);
        }
    }

    private ToggleButton createNavButton(WorkbenchModule module) {
        ToggleButton button = new ToggleButton(module.getName());
        if (module.getIconLiteral() != null) {
            FontIcon icon = new FontIcon(module.getIconLiteral());
            icon.getStyleClass().add("workbench-nav-icon");
            button.setGraphic(icon);
            button.setGraphicTextGap(10);
        }
        button.getStyleClass().add("workbench-nav-button");
        button.setToggleGroup(navGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.selectedProperty().subscribe(selected -> {
            if (selected) {
                activateModule(module);
            }
        });
        navButtons.put(module, button);
        return button;
    }

    private void activateModule(WorkbenchModule module) {
        if (activeModule == module) {
            return;
        }
        if (activeModule != null) {
            activeModule.deactivate();
        }
        activeModule = module;
        contentPane.getChildren().setAll(module.activate());
    }

    public static final class Builder {

        private final List<WorkbenchModule> modules;
        private final List<WorkbenchModule> bottomModules = new ArrayList<>();

        private Builder(WorkbenchModule... modules) {
            this.modules = List.of(modules);
        }

        /**
         * Pins a module to the bottom of the sidebar, below a spacer. Call
         * order is preserved (e.g. About above Settings).
         */
        public Builder bottomModule(WorkbenchModule module) {
            this.bottomModules.add(module);
            return this;
        }

        public Workbench build() {
            return new Workbench(this);
        }
    }
}
