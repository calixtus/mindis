package org.mindis.gui.shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.util.Duration;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.Localization;

/// Minimal application shell: a permanent left sidebar with one navigation
/// entry per module (bottom-pinned entries supported, e.g. Settings) and the
/// active module's content on the right. Written from scratch against JavaFX
/// and AtlantaFX styling (see docs/adr/005-shell.md).
///
/// <p>Wrapped by a [com.dlsc.gemsfx.PowerPane] in `MinDisApp`,
/// which layers dialogs, notifications and a bottom drawer over this content.
///
/// <p>The sidebar is resizable: drag the handle on its right edge to change its
/// width. Dragged below [#COLLAPSE_THRESHOLD] it snaps to an icon-only
/// rail (labels hidden, module name shown as a tooltip); a chevron toggle at the
/// top expands it back to a labelled width. Inspired by FXComponents'
/// NavigationPane shrunken/unshrunken width model.
public final class AppShell extends BorderPane {

    /// Icon-only rail width.
    private static final double COLLAPSED_WIDTH = 60;
    /// Default width the chevron toggle expands to (within the 240-300px band
    /// UX guidance recommends for an expanded sidebar).
    private static final double EXPANDED_WIDTH = 256;
    /// Narrowest labelled width; below this the sidebar collapses.
    private static final double MIN_EXPANDED_WIDTH = 200;
    /// Widest the sidebar may be dragged.
    private static final double MAX_WIDTH = 360;
    /// Drag narrower than this and the sidebar snaps to the icon-only rail.
    private static final double COLLAPSE_THRESHOLD = 120;
    /// How far one arrow-key press moves the resize handle.
    private static final double KEYBOARD_RESIZE_STEP = 16;
    /// Width of the drag handle - a pointer target, not a visual element.
    private static final double HANDLE_WIDTH = 10;
    /// On the icon-only rail the tooltip *is* the label, so it may not keep the
    /// ~1s delay a supplementary hint would get.
    private static final Duration RAIL_TOOLTIP_DELAY = Duration.millis(300);
    /// Long enough to read as one movement, short enough not to be waited on.
    private static final Duration COLLAPSE_ANIMATION = Duration.millis(160);

    private final Map<ShellModule, ToggleButton> navButtons = new LinkedHashMap<>();
    private final Map<ShellModule, Tooltip> railTooltips = new LinkedHashMap<>();
    private final Map<ShellModule, Label> navRows = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane contentPane = new StackPane();
    private final List<ShellModule> modules;

    private final VBox sidebar = new VBox();
    private final FontIcon toggleIcon = new FontIcon();
    private final Tooltip toggleTooltip = new Tooltip();

    private @Nullable ShellModule activeModule;
    private boolean collapsed;
    /// Observable mirror of [#collapsed] so a sidebar header (e.g. the
    /// collection switcher) can adapt to the icon-only rail.
    private final ReadOnlyBooleanWrapper collapsedProperty = new ReadOnlyBooleanWrapper(this, "collapsed");
    private double dragStartSceneX;
    private double dragStartWidth;
    private double currentWidth;
    private final Timeline widthAnimation = new Timeline();

    private AppShell(Builder builder) {
        List<ShellModule> all = new ArrayList<>(builder.modules);
        all.addAll(builder.bottomModules);
        this.modules = List.copyOf(all);
        getStyleClass().add("shell");
        getStylesheets().add(AppShell.class.getResource("shell.css").toExternalForm());

        sidebar.getStyleClass().add("shell-sidebar");
        // Chevron on top of everything, then the sidebar header (collection
        // switcher), then the navigation entries.
        sidebar.getChildren().add(createToggleButton());
        if (builder.sidebarHeader != null) {
            builder.sidebarHeader.getStyleClass().add("shell-sidebar-header");
            sidebar.getChildren().add(builder.sidebarHeader);
        }
        VBox navList = new VBox();
        navList.getStyleClass().add("shell-nav-list");
        for (ShellModule module : builder.modules) {
            navList.getChildren().add(createNavButton(module));
        }

        // Only the module list scrolls. The bottom-pinned entries stay outside it,
        // so a window too short for every entry at once still leaves Settings and
        // About reachable instead of clipping them off the end.
        ScrollPane navScroll = new ScrollPane(navList);
        navScroll.getStyleClass().add("shell-nav-scroll");
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        navScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        // Also the spacer: growing to fill pushes the bottom-pinned entries down.
        VBox.setVgrow(navScroll, Priority.ALWAYS);
        sidebar.getChildren().add(navScroll);

        if (!builder.bottomModules.isEmpty()) {
            VBox bottomNav = new VBox();
            bottomNav.getStyleClass().add("shell-bottom-nav");
            for (ShellModule module : builder.bottomModules) {
                bottomNav.getChildren().add(createNavButton(module));
            }
            sidebar.getChildren().add(bottomNav);
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

    public static Builder builder(ShellModule... modules) {
        return new Builder(modules);
    }

    /// Every module, top and bottom-pinned alike (e.g. for disposing them all on a UI rebuild).
    public List<ShellModule> getModules() {
        return modules;
    }

    public @Nullable ShellModule getActiveModule() {
        return activeModule;
    }

    /// Whether the sidebar is collapsed to the icon-only rail; a sidebar header
    /// binds to this to hide its labels while collapsed.
    public ReadOnlyBooleanProperty collapsedProperty() {
        return collapsedProperty.getReadOnlyProperty();
    }

    /// Current sidebar width (icon-only rail width while collapsed), for
    /// persisting across restarts alongside window geometry.
    public double getSidebarWidth() {
        return currentWidth;
    }

    /// Rebuilds the active module's content in place.
    ///
    /// Selecting a module already calls [ShellModule#activate()]; this is for
    /// when the data underneath the active one was replaced without the selection
    /// changing - another document opened, say. A module that mirrors a
    /// [org.mindis.gui.data.LiveStore] follows such a change on its own, but one
    /// that reads a repository while building its content has no way to notice it.
    public void reloadActiveModule() {
        if (activeModule != null) {
            contentPane.getChildren().setAll(activeModule.activate());
        }
    }

    /// Selects the module in the sidebar (activating it).
    public void openModule(ShellModule module) {
        ToggleButton button = navButtons.get(module);
        if (button != null) {
            button.setSelected(true);
        }
    }

    /// Fully-qualified class name of the active module, or `null` if none.
    /// Stable across a UI rebuild (module instances are recreated) and
    /// independent of the localized module names, so it survives a language
    /// change - unlike a name or the module instance itself.
    public @Nullable String getActiveModuleClassName() {
        return activeModule == null ? null : activeModule.getClass().getName();
    }

    /// Selects the sidebar entry whose module has the given class name.
    public void openModule(@Nullable String className) {
        if (className == null) {
            return;
        }
        for (Map.Entry<ShellModule, ToggleButton> entry : navButtons.entrySet()) {
            if (entry.getKey().getClass().getName().equals(className)) {
                entry.getValue().setSelected(true);
                return;
            }
        }
    }

    private Button createToggleButton() {
        toggleIcon.getStyleClass().add("shell-nav-icon");
        Button button = new Button();
        button.setGraphic(toggleIcon);
        button.setTooltip(toggleTooltip);
        button.getStyleClass().add("shell-toggle-button");
        button.setMaxWidth(Double.MAX_VALUE);
        // Collapsed -> expand to a labelled width; expanded -> collapse to rail.
        button.setOnAction(_ -> setSidebarWidth(collapsed ? EXPANDED_WIDTH : COLLAPSED_WIDTH, true));
        return button;
    }

    private Region createResizeHandle() {
        Region handle = new Region();
        handle.getStyleClass().add("shell-resize-handle");
        handle.setCursor(Cursor.H_RESIZE);
        // 6px was a hard target to hit. The fill matches the sidebar's, so widening
        // it only moves the boundary line - it does not show up as a wider seam.
        handle.setMinWidth(HANDLE_WIDTH);
        handle.setPrefWidth(HANDLE_WIDTH);
        handle.setMaxHeight(Double.MAX_VALUE);
        handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            // Grabbing the handle mid-glide hands control over immediately, rather
            // than letting the animation keep writing widths under the drag.
            widthAnimation.stop();
            dragStartSceneX = event.getSceneX();
            dragStartWidth = sidebar.getWidth();
        });
        handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event ->
                setSidebarWidth(dragStartWidth + (event.getSceneX() - dragStartSceneX)));

        // Dragging is a mouse-only gesture, so the sidebar width was reachable only
        // with a pointer. Focus the handle and the arrow keys do the same thing.
        handle.setFocusTraversable(true);
        handle.setAccessibleRole(AccessibleRole.SLIDER);
        handle.setAccessibleText(Localization.lang("Sidebar width"));
        handle.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case LEFT -> nudgeSidebar(-KEYBOARD_RESIZE_STEP);
                case RIGHT -> nudgeSidebar(KEYBOARD_RESIZE_STEP);
                default -> {
                    return;
                }
            }
            event.consume();
        });
        return handle;
    }

    /// Arrow-key resizing, matching what a drag of the same direction would do:
    /// stepping left off the narrowest labelled width collapses to the rail, and
    /// from the rail only a step right leaves it - there is nothing narrower.
    private void nudgeSidebar(double delta) {
        if (collapsed) {
            if (delta > 0) {
                setSidebarWidth(EXPANDED_WIDTH, true);
            }
            return;
        }
        double next = currentWidth + delta;
        boolean collapsing = next < MIN_EXPANDED_WIDTH;
        // A step within the band is a nudge and should land at once; leaving the
        // band is the same change the chevron makes, so it glides the same way.
        setSidebarWidth(collapsing ? COLLAPSED_WIDTH : next, collapsing);
    }

    /// Applies a width without animating - what a drag wants, since the sidebar
    /// has to track the pointer rather than chase it.
    private void setSidebarWidth(double width) {
        setSidebarWidth(width, false);
    }

    /// Pins the sidebar to a width (min == pref == max so it never flexes in the
    /// enclosing HBox) and derives collapsed state from it: narrower than
    /// [#COLLAPSE_THRESHOLD] snaps to the icon-only rail.
    ///
    /// @param animated glide to the new width instead of jumping to it. Only for
    ///                 changes the user asked for as a whole - the chevron, an
    ///                 arrow key - never for a drag, which would then lag behind
    ///                 the pointer by the animation's duration.
    private void setSidebarWidth(double width, boolean animated) {
        boolean shouldCollapse = width < COLLAPSE_THRESHOLD;
        double applied = shouldCollapse
                ? COLLAPSED_WIDTH
                : Math.min(MAX_WIDTH, Math.max(MIN_EXPANDED_WIDTH, width));
        currentWidth = applied;
        widthAnimation.stop();
        if (animated) {
            // Collapsed state flips up front, not when the glide ends: collapsing
            // drops the labels before the sidebar narrows onto them, and expanding
            // lets the widening sidebar reveal them instead of popping them in.
            setCollapsed(shouldCollapse);
            widthAnimation.getKeyFrames().setAll(new KeyFrame(COLLAPSE_ANIMATION,
                    new KeyValue(sidebar.minWidthProperty(), applied, Interpolator.EASE_BOTH),
                    new KeyValue(sidebar.prefWidthProperty(), applied, Interpolator.EASE_BOTH),
                    new KeyValue(sidebar.maxWidthProperty(), applied, Interpolator.EASE_BOTH)));
            widthAnimation.playFromStart();
            return;
        }
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
        collapsedProperty.set(value);
        navButtons.forEach(this::applyButtonMode);
        updateToggleIcon();
    }

    private void updateToggleIcon() {
        toggleIcon.setIconLiteral(collapsed ? "mdi2c-chevron-right" : "mdi2c-chevron-left");
        toggleTooltip.setText(collapsed ? Localization.lang("Expand") : Localization.lang("Collapse"));
    }

    /// The entry's content is one row built here rather than the button's own
    /// text-plus-graphic pair, because a badge has to sit at the far end of it and
    /// a `ToggleButton` has no slot for a third thing after its label.
    private ToggleButton createNavButton(ShellModule module) {
        ToggleButton button = new ToggleButton();
        HBox row = new HBox();
        row.getStyleClass().add("shell-nav-row");
        // Left-aligned while there is a label to read along; centred on the rail,
        // where the icon is the whole entry and has to sit in the middle of it.
        row.alignmentProperty().bind(
                Bindings.when(collapsedProperty).then(Pos.CENTER).otherwise(Pos.CENTER_LEFT));

        String iconLiteral = module.getIconLiteral();
        if (iconLiteral != null) {
            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add("shell-nav-icon");
            // Outline at rest, filled when active: the selected entry then differs
            // in glyph weight as well as in colour, which the rail needs most -
            // there is no label there to carry the distinction.
            String selectedLiteral = module.getSelectedIconLiteral();
            button.selectedProperty().subscribe(selected ->
                    icon.setIconLiteral(selected ? selectedLiteral : iconLiteral));
            row.getChildren().add(new StackPane(icon, dotBadge(module)));
        }

        Label name = new Label(module.getName());
        name.getStyleClass().add("shell-nav-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(name, pillBadge(module));
        navRows.put(module, name);

        button.setGraphic(row);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().add("shell-nav-button");
        button.setToggleGroup(navGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        // A Labeled lays its graphic out at the graphic's own preferred width, so
        // without this the row would be as wide as its contents and the badge
        // would sit against the name instead of at the entry's far edge. Insets
        // rather than a literal, so the button's padding stays a CSS concern.
        row.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> button.getWidth() - button.getInsets().getLeft() - button.getInsets().getRight(),
                button.widthProperty(), button.insetsProperty()));
        button.selectedProperty().subscribe(selected -> {
            if (selected) {
                activateModule(module);
            }
        });
        navButtons.put(module, button);
        applyButtonMode(module, button);
        return button;
    }

    /// Shows or hides the module label per collapsed state; when collapsed the
    /// name moves to a tooltip so the icon-only rail stays legible.
    private void applyButtonMode(ShellModule module, ToggleButton button) {
        boolean iconOnly = collapsed && module.getIconLiteral() != null;
        button.getStyleClass().remove("shell-nav-button-collapsed");
        Label name = navRows.get(module);
        if (name != null) {
            name.setVisible(!iconOnly);
            name.setManaged(!iconOnly);
        }
        if (iconOnly) {
            button.setTooltip(railTooltip(module));
            button.getStyleClass().add("shell-nav-button-collapsed");
        } else {
            button.setTooltip(null);
        }
    }

    /// The count as a pill after the entry's name; nothing at all at zero, and
    /// nothing on the rail, where [#dotBadge] stands in for it.
    private Label pillBadge(ShellModule module) {
        Label badge = new Label();
        badge.getStyleClass().add("shell-nav-badge");
        badge.textProperty().bind(module.badgeCountProperty().asString());
        badge.visibleProperty().bind(
                module.badgeCountProperty().greaterThan(0).and(collapsedProperty.not()));
        badge.managedProperty().bind(badge.visibleProperty());
        return badge;
    }

    /// On the rail there is no room for a number, so the count becomes a dot in
    /// the icon's corner - present or absent is the whole message there.
    private StackPane dotBadge(ShellModule module) {
        StackPane dot = new StackPane();
        dot.getStyleClass().add("shell-nav-badge-dot");
        dot.setMaxSize(8, 8);
        dot.setMinSize(8, 8);
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        dot.setMouseTransparent(true);
        dot.visibleProperty().bind(
                module.badgeCountProperty().greaterThan(0).and(collapsedProperty));
        dot.managedProperty().bind(dot.visibleProperty());
        return dot;
    }

    /// The module's rail tooltip, built once per module rather than on every collapse.
    private Tooltip railTooltip(ShellModule module) {
        return railTooltips.computeIfAbsent(module, key -> {
            Tooltip tooltip = new Tooltip(key.getName());
            tooltip.setShowDelay(RAIL_TOOLTIP_DELAY);
            return tooltip;
        });
    }

    private void activateModule(ShellModule module) {
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

        private final List<ShellModule> modules;
        private final List<ShellModule> bottomModules = new ArrayList<>();
        private double initialSidebarWidth = EXPANDED_WIDTH;
        private @Nullable Node sidebarHeader;

        private Builder(ShellModule... modules) {
            this.modules = List.of(modules);
        }

        /// Places a node at the very top of the sidebar, above the collapse
        /// toggle and the navigation entries (e.g. the collection switcher).
        /// Gets the `shell-sidebar-header` style class.
        public Builder sidebarHeader(Node header) {
            this.sidebarHeader = header;
            return this;
        }

        /// Pins a module to the bottom of the sidebar, below a spacer. Call
        /// order is preserved (e.g. About above Settings).
        public Builder bottomModule(ShellModule module) {
            this.bottomModules.add(module);
            return this;
        }

        /// Sidebar width to start with (e.g. a previously persisted width);
        /// defaults to [#EXPANDED_WIDTH]. Clamped and collapse-checked
        /// the same as a drag, so any value (including a stale one from before
        /// [#MIN_EXPANDED_WIDTH]/[#MAX_WIDTH] changed) is safe.
        public Builder initialSidebarWidth(double width) {
            this.initialSidebarWidth = width;
            return this;
        }

        public AppShell build() {
            return new AppShell(this);
        }
    }
}
