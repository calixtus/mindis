package org.mindis.gui.modules;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.StringConverter;
import javafx.util.Subscription;

import atlantafx.base.controls.ToggleSwitch;
import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.paging.PagingControls;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.LiturgicalServices;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServiceCsvMapper;
import org.mindis.core.persistence.TemplateRepository;
import org.jspecify.annotations.Nullable;

import org.mindis.gui.planning.ArchivedPlansDialog;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.gui.shell.CrudModule;
import org.mindis.gui.shell.ShellOverlays;
import org.mindis.gui.shell.Toolbars;
import org.mindis.gui.data.LiveStore;

/// Liturgical services module: individual date/time services (plus generation
/// from weekly templates), filling their role slots either manually or by
/// running the solver, and the solve/export/archive workflow around that.
///
/// <p>An assignment lives directly on its [org.mindis.core.model.Slot] (see
/// that class and [org.mindis.core.planning.PlanningService]), so a service
/// <em>is</em> its own plan: picking a server, auto-filling, or solving just
/// rewrites the service's slots and stages them into the shared [LiveStore]
/// like any other service edit - one Save of the document persists them. There
/// is no separate plan object, no plan-dirty state and no date-range
/// bookkeeping. A [org.mindis.core.planning.ServicePlan] is built transiently
/// only when the solver runs (or to compute a score / per-slot violations) and
/// discarded once its results are written back onto the services.
///
/// <p>The row tile ([ServiceTiles]), the row editor ([ServiceEditor]) and the
/// archive/export actions ([ServicePlanActions]) are their own classes; this
/// one assembles the screen and its toolbar.
public final class ServicesModule extends CrudModule<LiturgicalService> {

    // Auto-fill only leaves one service's slots free - a far smaller problem
    // than a whole-plan solve, so it doesn't need the full solverSecondsLimit.
    private static final Duration AUTO_FILL_TIME_BUDGET = Duration.ofSeconds(5);
    static final String STYLESHEET = ServicesModule.class.getResource("services.css").toExternalForm();
    private static final String GENERATE_POPUP_STYLE = """
            -fx-background-color: -color-bg-overlay;
            -fx-border-color: -color-border-default;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            """;
    /// How many masses [#table()] shows at once - paged because this list
    /// can grow into the hundreds once a year or more has been generated.
    private static final int PAGE_SIZE = 20;

    private final ServicesViewModel viewModel;
    private final PlanningViewModel planningViewModel;
    private final LiveRoster roster;
    private final ServiceTiles tiles;
    private final ServicePlanActions planActions;

    // Chronological, not the store's raw order - a windowed table only reads
    // sensibly page-to-page if each page is a contiguous date range.
    private final SortedList<LiturgicalService> sortedServices =
            new SortedList<>(store().items(), Comparator.comparing(LiturgicalService::dateTime));
    private final ObservableList<LiturgicalService> pageItems = FXCollections.observableArrayList();
    private final PagingControls pagingControls = new PagingControls();

    private final CalendarPicker fromPicker = CalendarPickers.create();
    private final CalendarPicker toPicker = CalendarPickers.create();
    /// The currently running solve job, if any - View-local bookkeeping so the
    /// abort action knows what to cancel.

    // A tile's rendered text depends on more than its own service record: the
    // role and server display names come from the roster stores. Those are the
    // one dependency a row can't react to through its own item being replaced
    // (a server renamed in another module leaves this service's record
    // untouched), so the tiles re-render off a single subscription to both
    // stores here - the reactive replacement for the table().refresh() calls
    // this module used to sprinkle through every handler. Every other trigger
    // (a pick, a solve, an archive) already replaces or removes the row's own
    // item, which re-renders that row on its own.
    private final Subscription tileDependencySubscription;
    private final ServicesSolverController solver;

    public ServicesModule(String name, LiveStore<LiturgicalService> serviceStore, LiveStore<Role> roleStore,
                          LiveStore<Server> serverStore, TemplateRepository templateRepository,
                          RoleRepository roleRepository, PlanningViewModel planningViewModel,
                          ShellOverlays overlays) {
        super(name, "mdi2c-church-outline", "mdi2c-church", serviceStore, overlays);
        this.viewModel = new ServicesViewModel(templateRepository);
        this.planningViewModel = planningViewModel;
        this.roster = new LiveRoster(roleStore, serverStore);
        this.tiles = new ServiceTiles(roster);
        this.planActions = new ServicePlanActions(planningViewModel, serviceStore);
        this.solver = new ServicesSolverController(planningViewModel,
                () -> store().items(), this::mergeLive, overlays);

        // The sidebar badge: slots nobody has been put in yet, for services still
        // to come. Past services are left out - an unfilled slot last month is a
        // record, not a job. Counted off the store's in-memory list on every
        // change, so no repository is read for it.
        store().items().subscribe(this::refreshOpenSlotBadge);

        // The table is used as a single-column tile list: each row's cell
        // renders the whole date/type/location + role-slot summary.
        TableColumn<LiturgicalService, LiturgicalService> tileColumn = new TableColumn<>();
        tileColumn.setSortable(false);
        tileColumn.setReorderable(false);
        tileColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        tileColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LiturgicalService service, boolean empty) {
                super.updateItem(service, empty);
                setText(null);
                setGraphic(empty || service == null ? null : tiles.build(service));
            }
        });
        tileColumn.prefWidthProperty().bind(table().widthProperty().subtract(18));
        table().getColumns().add(tileColumn);
        table().setTableMenuButtonVisible(false);
        table().getStyleClass().add("services-tile-table");
        table().getStylesheets().add(STYLESHEET);
        // Re-render the tiles when a role or server changes anywhere (its
        // display name appears on the tiles) - see tileDependencySubscription.
        tileDependencySubscription = serverStore.items().subscribe(() -> table().refresh())
                .and(roleStore.items().subscribe(() -> table().refresh()));

        pagingControls.setPageSize(PAGE_SIZE);
        pagingControls.setShowPageSizeSelector(false);
        pagingControls.pageProperty().addListener((obs, oldPage, newPage) -> refreshPageItems());
        sortedServices.addListener((ListChangeListener<LiturgicalService>) change -> refreshPageItems());
        refreshPageItems();

        fromPicker.setPromptText(Localization.lang("From"));
        fromPicker.setPrefWidth(130);
        toPicker.setPromptText(Localization.lang("To"));
        toPicker.setPrefWidth(130);
        LocalDate firstOfNextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        fromPicker.setValue(firstOfNextMonth);
        toPicker.setValue(firstOfNextMonth.plusMonths(1).minusDays(1));

        Button generateButton = Toolbars.button(Localization.lang("Generate from templates"), "mdi2c-calendar-plus");
        generateButton.setOnAction(event -> showGenerateFromTemplatesPopup(generateButton));

        Button newButton = newButton();
        Button deleteButton = deleteButton();
        Button importButton = importButton(new ServiceCsvMapper(roleRepository));

        ReadOnlyBooleanProperty solving = planningViewModel.solvingProperty();
        // While solving, the Autofill button is swapped for a progress bar
        // (filled/total slots) that doubles as the abort control - one toolbar
        // slot, never both at once.
        StackPane autofillSlot = new StackPane();
        Button autofillButton = Toolbars.button(Localization.lang("Autofill..."), "mdi2a-auto-fix");
        autofillButton.disableProperty().bind(Bindings.isEmpty(store().items()));
        // Stays managed (reserving its width) even when hidden, so the progress
        // bar drops into the exact same footprint with no toolbar reflow.
        autofillButton.visibleProperty().bind(solving.not());
        autofillButton.setOnAction(event -> showAutofillPopup(autofillSlot));
        // Fills as the solver assigns more slots; a click asks to abort (and
        // that prompt auto-dismisses if the solve finishes first). Sized to the
        // button so it is a pure drop-in replacement.
        ProgressBar solveProgressBar = new ProgressBar(0);
        solveProgressBar.prefWidthProperty().bind(autofillButton.widthProperty());
        solveProgressBar.progressProperty().bind(planningViewModel.solveProgressProperty());
        solveProgressBar.visibleProperty().bind(solving);
        solveProgressBar.setCursor(Cursor.HAND);
        Tooltip.install(solveProgressBar, new Tooltip(Localization.lang("Abort autofill")));
        solveProgressBar.setOnMouseClicked(event -> solver.confirmAbort());
        autofillSlot.getChildren().addAll(autofillButton, solveProgressBar);
        Button exportPlanButton = Toolbars.button(Localization.lang("Export..."), "mdi2e-export");
        exportPlanButton.disableProperty().bind(solving.or(Bindings.isEmpty(store().items())));
        exportPlanButton.setOnAction(event -> showExportPopup(exportPlanButton));
        Button archiveButton = Toolbars.button(Localization.lang("Archived plans"), "mdi2a-archive");
        archiveButton.setOnAction(event ->
                ArchivedPlansDialog.show(planningViewModel, table().getScene().getWindow(), planActions::archive));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL),
                generateButton,
                new Separator(Orientation.VERTICAL), importButton, exportPlanButton,
                new Separator(Orientation.VERTICAL),
                autofillSlot, archiveButton);
    }

    /// Lightweight popup for "Generate from templates", anchored under the
    /// toolbar button. `ServiceGenerator` only proposes occurrences not
    /// already present, and [#mergeLive] only ever appends unmatched
    /// rows, so this never touches an existing service's slots or assignments.
    private void showGenerateFromTemplatesPopup(Node anchor) {
        CalendarPicker popupFrom = CalendarPickers.create();
        popupFrom.setValue(fromPicker.getValue());
        CalendarPicker popupTo = CalendarPickers.create();
        popupTo.setValue(toPicker.getValue());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(Localization.lang("From")), 0, 0);
        grid.add(popupFrom, 1, 0);
        grid.add(new Label(Localization.lang("To")), 0, 1);
        grid.add(popupTo, 1, 1);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button okButton = new Button(Localization.lang("OK"));
        okButton.setOnAction(event -> {
            List<LiturgicalService> generated = viewModel.generateFromTemplates(
                    popupFrom.getValue(), popupTo.getValue(), store().items());
            if (generated != null) {
                mergeLive(generated);
            }
            popup.hide();
        });
        HBox buttonRow = new HBox(okButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10, grid, buttonRow);
        content.setPadding(new Insets(12));
        content.setStyle(GENERATE_POPUP_STYLE);
        popup.getContent().add(content);

        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, anchorBounds.getMinX(), anchorBounds.getMaxY() + 4);
    }

    /// Popup for the solve actions: From/To bounds (blank From = "from the
    /// earliest service", blank To = "all future services") plus an "Overwrite
    /// already-assigned slots" toggle. "Autofill" fills every open slot of
    /// every service in the window (honoring those bounds and the toggle);
    /// "Solve all" ignores the bounds and re-solves the whole board's
    /// non-pinned slots in one go.
    private void showAutofillPopup(Node anchor) {
        CalendarPicker popupFrom = CalendarPickers.create();
        popupFrom.setValue(LocalDate.now());
        CalendarPicker popupTo = CalendarPickers.create();

        ToggleSwitch overwriteToggle = new ToggleSwitch(Localization.lang("Overwrite already-assigned slots"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(Localization.lang("From")), 0, 0);
        grid.add(popupFrom, 1, 0);
        grid.add(new Label(Localization.lang("To")), 0, 1);
        grid.add(popupTo, 1, 1);
        grid.add(overwriteToggle, 0, 2, 2, 1);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button solveAllButton = new Button(Localization.lang("Solve all"));
        solveAllButton.setOnAction(event -> {
            popup.hide();
            solver.solveAll();
        });
        Button okButton = new Button(Localization.lang("Autofill"));
        okButton.setOnAction(event -> {
            popup.hide();
            onAutofill(popupFrom.getValue(), popupTo.getValue(), overwriteToggle.isSelected());
        });
        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(8, solveAllButton, buttonSpacer, okButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, grid, buttonRow);
        content.setPadding(new Insets(12));
        content.setStyle(GENERATE_POPUP_STYLE);
        popup.getContent().add(content);

        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, anchorBounds.getMinX(), anchorBounds.getMaxY() + 4);
    }

    /// Popup for exporting the plan, anchored under the toolbar button:
    /// From/To date bounds (blank From = from the earliest service, blank To =
    /// every later one) plus the output format that used to live on the export
    /// menu button. Mirrors the Generate/Autofill popups.
    private void showExportPopup(Node anchor) {
        CalendarPicker popupFrom = CalendarPickers.create();
        popupFrom.setValue(fromPicker.getValue());
        CalendarPicker popupTo = CalendarPickers.create();
        popupTo.setValue(toPicker.getValue());

        ComboBox<PlanExportFormat> formatBox =
                new ComboBox<>(FXCollections.observableArrayList(PlanExportFormat.values()));
        formatBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable PlanExportFormat format) {
                return format == null ? "" : format.name();
            }

            @Override
            public @Nullable PlanExportFormat fromString(@Nullable String string) {
                return null;
            }
        });
        formatBox.getSelectionModel().select(PlanExportFormat.PDF);
        formatBox.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(Localization.lang("From")), 0, 0);
        grid.add(popupFrom, 1, 0);
        grid.add(new Label(Localization.lang("To")), 0, 1);
        grid.add(popupTo, 1, 1);
        grid.add(new Label(Localization.lang("Format")), 0, 2);
        grid.add(formatBox, 1, 2);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button okButton = new Button(Localization.lang("Export"));
        okButton.setOnAction(event -> {
            PlanExportFormat format = formatBox.getValue();
            if (format == null) {
                return;
            }
            popup.hide();
            planActions.export(table().getScene().getWindow(), popupFrom.getValue(), popupTo.getValue(), format);
        });
        HBox buttonRow = new HBox(okButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10, grid, buttonRow);
        content.setPadding(new Insets(12));
        content.setStyle(GENERATE_POPUP_STYLE);
        popup.getContent().add(content);

        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, anchorBounds.getMinX(), anchorBounds.getMaxY() + 4);
    }

    /// Runs a windowed Autofill: builds a problem over every live service (so
    /// spacing/fairness see the whole board), leaves only the eligible slots
    /// free (in `[from, to]`, and either open or - if `overwrite` -
    /// already assigned), solves, then writes the results back onto the
    /// services. A blank bound is treated as unbounded.
    private void onAutofill(@Nullable LocalDate from, @Nullable LocalDate to, boolean overwrite) {
        if (planningViewModel.solvingProperty().get()) {
            return;
        }
        solver.autofillWindow(from, to, overwrite);
    }

    @Override
    protected ObservableList<LiturgicalService> tableItems() {
        return pageItems;
    }

    @Override
    protected Node belowTable() {
        return pagingControls;
    }

    /// Recomputes [#pageItems] for the current page, clamping the page
    /// down if a deletion left it past the new last page.
    private void refreshPageItems() {
        int total = sortedServices.size();
        pagingControls.setTotalItemCount(total);
        int pageSize = pagingControls.getPageSize();
        int lastPage = pageSize <= 0 ? 0 : Math.max(0, (total - 1) / pageSize);
        if (pagingControls.getPage() > lastPage) {
            pagingControls.setPage(lastPage);
            return;
        }
        int from = Math.min(pagingControls.getPage() * pageSize, total);
        int to = Math.min(from + pageSize, total);
        pageItems.setAll(sortedServices.subList(from, to));
    }

    @Override
    protected LiturgicalService createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<LiturgicalService> buildEditor(LiturgicalService service) {
        ServiceEditor editor = new ServiceEditor(service, baseline(service), this::updateLive, roster,
                planningViewModel, solver);
        return new EditorBinding<>(editor.node(), editor::refresh, editor::dispose);
    }

    @Override
    public void dispose() {
        tileDependencySubscription.unsubscribe();
        super.dispose();
    }

    /// Whether the solver is currently running - the global Save action stays disabled while
    /// true.
    public ReadOnlyBooleanProperty solvingProperty() {
        return planningViewModel.solvingProperty();
    }

    /// Recounts the badge from what the store currently holds.
    private void refreshOpenSlotBadge() {
        setBadgeCount(LiturgicalServices.openSlotsAhead(store().items(), LocalDateTime.now()));
    }
}
