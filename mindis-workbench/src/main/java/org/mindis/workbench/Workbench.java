package org.mindis.workbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Minimal workbench shell: a permanent left sidebar with one navigation entry
 * per module (bottom-pinned entries supported, e.g. Settings) and the active
 * module's content on the right. API modeled on WorkbenchFX (Apache-2.0),
 * implemented from scratch against JavaFX 26 and AtlantaFX styling
 * (see docs/adr/005-workbench-shell.md).
 *
 * <p>The sidebar is resizable: drag the handle on its right edge to change its
 * width. Dragged below {@link #COLLAPSE_THRESHOLD} it snaps to an icon-only
 * rail (labels hidden, module name shown as a tooltip); a chevron toggle at the
 * top expands it back to a labelled width. Inspired by FXComponents'
 * NavigationPane shrunken/unshrunken width model.
 */
public final class Workbench extends BorderPane {

    /** Icon-only rail width. */
    private static final double COLLAPSED_WIDTH = 60;
    /** Default width the chevron toggle expands to. */
    private static final double EXPANDED_WIDTH = 180;
    /** Narrowest labelled width; below this the sidebar collapses. */
    private static final double MIN_EXPANDED_WIDTH = 140;
    /** Widest the sidebar may be dragged. */
    private static final double MAX_WIDTH = 360;
    /** Drag narrower than this and the sidebar snaps to the icon-only rail. */
    private static final double COLLAPSE_THRESHOLD = 120;

    private final Map<WorkbenchModule, ToggleButton> navButtons = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane contentPane = new StackPane();
    private final List<WorkbenchModule> modules;

    private final VBox sidebar = new VBox();
    private final FontIcon toggleIcon = new FontIcon();
    private final Tooltip toggleTooltip = new Tooltip();

    private @Nullable WorkbenchModule activeModule;
    private boolean collapsed;
    private double dragStartSceneX;
    private double dragStartWidth;
    private double currentWidth;

    private Workbench(Builder builder) {
        List<WorkbenchModule> all = new ArrayList<>(builder.modules);
        all.addAll(builder.bottomModules);
        this.modules = List.copyOf(all);
        getStyleClass().add("workbench");
        getStylesheets().add(Workbench.class.getResource("workbench.css").toExternalForm());

        sidebar.getStyleClass().add("workbench-sidebar");
        sidebar.getChildren().add(createToggleButton());
        for (WorkbenchModule module : builder.modules) {
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

        setLeft(new HBox(sidebar, createResizeHandle()));
        setCenter(contentPane);

        setSidebarWidth(builder.initialSidebarWidth);
        updateToggleIcon();

        if (!navButtons.isEmpty()) {
            navButtons.values().iterator().next().setSelected(true);
        }
    }

    public static Builder builder(WorkbenchModule... modules) {
        return new Builder(modules);
    }

    /** Every module, top and bottom-pinned alike (e.g. for disposing them all on a UI rebuild). */
    public List<WorkbenchModule> getModules() {
        return modules;
    }

    public @Nullable WorkbenchModule getActiveModule() {
        return activeModule;
    }

    /**
     * Current sidebar width (icon-only rail width while collapsed), for
     * persisting across restarts alongside window geometry.
     */
    public double getSidebarWidth() {
        return currentWidth;
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

    /**
     * Fully-qualified class name of the active module, or {@code null} if none.
     * Stable across a UI rebuild (module instances are recreated) and
     * independent of the localized module names, so it survives a language
     * change - unlike a name or the module instance itself.
     */
    public @Nullable String getActiveModuleClassName() {
        return activeModule == null ? null : activeModule.getClass().getName();
    }

    /** Selects the sidebar entry whose module has the given class name. */
    public void openModule(@Nullable String className) {
        if (className == null) {
            return;
        }
        for (Map.Entry<WorkbenchModule, ToggleButton> entry : navButtons.entrySet()) {
            if (entry.getKey().getClass().getName().equals(className)) {
                entry.getValue().setSelected(true);
                return;
            }
        }
    }

    private Button createToggleButton() {
        toggleIcon.getStyleClass().add("workbench-nav-icon");
        Button button = new Button();
        button.setGraphic(toggleIcon);
        button.setTooltip(toggleTooltip);
        button.getStyleClass().add("workbench-toggle-button");
        button.setMaxWidth(Double.MAX_VALUE);
        // Collapsed -> expand to a labelled width; expanded -> collapse to rail.
        button.setOnAction(_ -> setSidebarWidth(collapsed ? EXPANDED_WIDTH : COLLAPSED_WIDTH));
        return button;
    }

    private Region createResizeHandle() {
        Region handle = new Region();
        handle.getStyleClass().add("workbench-resize-handle");
        handle.setCursor(Cursor.H_RESIZE);
        handle.setMinWidth(6);
        handle.setPrefWidth(6);
        handle.setMaxHeight(Double.MAX_VALUE);
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            dragStartSceneX = event.getSceneX();
            dragStartWidth = sidebar.getWidth();
        });
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event ->
                setSidebarWidth(dragStartWidth + (event.getSceneX() - dragStartSceneX)));
        return handle;
    }

    /**
     * Pins the sidebar to a width (min == pref == max so it never flexes in the
     * enclosing HBox) and derives collapsed state from it: narrower than
     * {@link #COLLAPSE_THRESHOLD} snaps to the icon-only rail.
     */
    private void setSidebarWidth(double width) {
        boolean shouldCollapse = width < COLLAPSE_THRESHOLD;
        double applied = shouldCollapse
                ? COLLAPSED_WIDTH
                : Math.min(MAX_WIDTH, Math.max(MIN_EXPANDED_WIDTH, width));
        currentWidth = applied;
        sidebar.setMinWidth(applied);
        sidebar.setPrefWidth(applied);
        sidebar.setMaxWidth(applied);
        setCollapsed(shouldCollapse);
    }

    private void setCollapsed(boolean value) {
        if (collapsed == value) {
            return;
        }
        collapsed = value;
        navButtons.forEach(this::applyButtonMode);
        updateToggleIcon();
    }

    private void updateToggleIcon() {
        toggleIcon.setIconLiteral(collapsed ? "mdi2c-chevron-right" : "mdi2c-chevron-left");
        toggleTooltip.setText(collapsed ? "Expand" : "Collapse");
    }

    private ToggleButton createNavButton(WorkbenchModule module) {
        ToggleButton button = new ToggleButton();
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
        applyButtonMode(module, button);
        return button;
    }

    /**
     * Shows or hides the module label per collapsed state; when collapsed the
     * name moves to a tooltip so the icon-only rail stays legible.
     */
    private void applyButtonMode(WorkbenchModule module, ToggleButton button) {
        boolean iconOnly = collapsed && module.getIconLiteral() != null;
        button.getStyleClass().remove("workbench-nav-button-collapsed");
        if (iconOnly) {
            button.setText(null);
            button.setTooltip(new Tooltip(module.getName()));
            button.getStyleClass().add("workbench-nav-button-collapsed");
        } else {
            button.setText(module.getName());
            button.setTooltip(null);
        }
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
        private double initialSidebarWidth = EXPANDED_WIDTH;

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

        /**
         * Sidebar width to start with (e.g. a previously persisted width);
         * defaults to {@link #EXPANDED_WIDTH}. Clamped and collapse-checked
         * the same as a drag, so any value (including a stale one from before
         * {@link #MIN_EXPANDED_WIDTH}/{@link #MAX_WIDTH} changed) is safe.
         */
        public Builder initialSidebarWidth(double width) {
            this.initialSidebarWidth = width;
            return this;
        }

        public Workbench build() {
            return new Workbench(this);
        }
    }
}
