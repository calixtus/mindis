package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.CalendarPicker;
import com.dlsc.gemsfx.ChipView;
import com.dlsc.gemsfx.SearchField;
import com.dlsc.gemsfx.TimePicker;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.RoleRepository;
import org.jspecify.annotations.Nullable;

import org.mindis.core.persistence.ServerCsvMapper;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.util.CalendarPickers;
import org.mindis.gui.util.SearchFields;
import org.mindis.gui.util.TimePickers;
import org.mindis.workbench.CrudModule;
import org.mindis.workbench.CsvRowMapper;
import org.mindis.workbench.LiveStore;

/// Altar server roster module: personal details, role qualifications and
/// unavailability periods (both part of the {@link Server} model). The
/// qualifications checklist binds to the shared live role list, so roles
/// created or edited (even unsaved) in the Roles module appear immediately.
public class ServersModule extends CrudModule<Server> {

    // Checkbox list row height as a multiple of the app font size.
    private static final double CELL_SIZE_FONT_FACTOR = 2.0;
    private static final double EDITOR_MIN_HEIGHT = 520;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ServersViewModel viewModel;
    private final UiPreferences uiPreferences;
    private final LiveStore<Role> roleStore;
    // Repaints the table when the shared role list changes, so an unsaved
    // role rename shows in the Qualifications column immediately. A field so
    // dispose() can detach it from the module-outliving store list.
    private final ListChangeListener<Role> roleChangeListener = change -> table().refresh();

    public ServersModule(String name, LiveStore<Server> serverStore, LiveStore<Role> roleStore,
                         ServerRepository serverRepository, RoleRepository roleRepository,
                         UiPreferences uiPreferences) {
        super(name, "mdi2a-account-group", serverStore);
        this.viewModel = new ServersViewModel(serverRepository, roleRepository);
        this.uiPreferences = uiPreferences;
        this.roleStore = roleStore;
        roleStore.items().addListener(roleChangeListener);

        TableColumn<Server, String> nameColumn = new TableColumn<>(Localization.lang("Name"));
        nameColumn.setPrefWidth(180);
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));

        TableColumn<Server, String> qualificationsColumn = new TableColumn<>(Localization.lang("Qualifications"));
        qualificationsColumn.setPrefWidth(160);
        qualificationsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().qualifications().stream()
                        .map(viewModel::roleName)
                        .sorted()
                        .collect(Collectors.joining(", "))));

        TableColumn<Server, String> activeColumn = new TableColumn<>(Localization.lang("Active"));
        activeColumn.setPrefWidth(60);
        activeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().active() ? Localization.lang("Yes") : Localization.lang("No")));

        table().getColumns().add(nameColumn);
        table().getColumns().add(qualificationsColumn);
        table().getColumns().add(activeColumn);

        Button newButton = new Button(Localization.lang("New"));
        newButton.setOnAction(event -> newItem());
        Button deleteButton = new Button(Localization.lang("Delete"));
        deleteButton.disableProperty().bind(table().getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> deleteSelected());

        ServerCsvMapper serverCsvMapper = new ServerCsvMapper(roleRepository);
        CsvRowMapper<Server> csvMapper =
                CsvRowMapper.of(serverCsvMapper::header, serverCsvMapper::toRow, serverCsvMapper::fromRow);
        Button exportButton = new Button(Localization.lang("Export"));
        exportButton.setOnAction(event -> exportCsv(csvMapper));
        Button importButton = new Button(Localization.lang("Import"));
        importButton.setOnAction(event -> importCsv(csvMapper,
                (imported, total) -> Localization.lang("%0 of %1 rows imported", imported, total)));

        toolbarExtras().addAll(newButton, deleteButton, new Separator(Orientation.VERTICAL), exportButton, importButton);
    }

    @Override
    public void dispose() {
        roleStore.items().removeListener(roleChangeListener);
        super.dispose();
    }

    @Override
    protected Server createStub() {
        return viewModel.createStub();
    }

    @Override
    protected EditorBinding<Server> buildEditor(Server server) {
        // Compares against the last-flushed value, not server itself - see
        // CrudModule#markDirtyOnChange. The scalar fields below (name/
        // contact/birth date/family/experienced/active) use markDirtyOnChange
        // directly; the collection-backed ones (qualifications, preferred
        // times, unavailable periods) each get their own recompute*Changed
        // Runnable instead, re-diffing the *whole* live set/list against the
        // baseline on every mutation - a single label spanning a whole list
        // needs that, not markDirtyOnChange's single-property model (the
        // same reasoning ServicesModule's own slot-count editor already
        // follows for its one label).
        Supplier<Server> baselineSupplier = () -> Objects.requireNonNullElse(savedSnapshot(server), server);

        TextField firstNameField = new TextField(server.firstName());
        TextField lastNameField = new TextField(server.lastName());
        TextField contactField = new TextField(server.contact());
        CalendarPicker birthDatePicker = CalendarPickers.create();
        birthDatePicker.setValue(server.birthDate());
        SearchField<String> familyIdField = buildFamilyIdField(server.familyId());
        ObservableList<LocalTime> preferredTimesItems = FXCollections.observableArrayList(
                server.preferredTimes().stream().sorted().toList());
        FlowPane preferredTimesTiles = new FlowPane(6, 6);
        // Same fix as the "Unavailable periods" row below: a FlowPane has no
        // default "grow to fill" the way Controls do, so without an explicit
        // max width it can't be stretched to the field column's real width
        // and wraps/squeezes its chips + picker at a narrow fixed size
        // regardless of how much room is actually free. Bound to
        // firstNameField rather than this FlowPane's own parent (there's no
        // intermediate wrapper here to bind to, unlike unavailabilityBox) -
        // firstNameField sits in the same grid column and, being a Control,
        // is already reliably stretched to that column's actual width.
        preferredTimesTiles.setMaxWidth(Double.MAX_VALUE);
        preferredTimesTiles.prefWrapLengthProperty().bind(firstNameField.widthProperty());
        TimePicker preferredTimePicker = TimePickers.create();
        Button addPreferredTimeButton = new Button(null, new FontIcon("mdi2p-plus"));
        // A plain Button's own default padding computes a taller natural height
        // than the TimePicker's - binding prefHeight to the picker's height alone
        // (tried first) didn't fix it because minHeight, left at its own larger
        // computed default, is what actually floors the final layout height
        // (JavaFX clamps to at least minHeight). Pinning minHeight to track
        // prefHeight lets it actually shrink to match, keeping the two controls
        // visually aligned despite sitting as separate controls, not a pill.
        addPreferredTimeButton.setMinHeight(Region.USE_PREF_SIZE);
        addPreferredTimeButton.setMaxHeight(Region.USE_PREF_SIZE);
        addPreferredTimeButton.prefHeightProperty().bind(preferredTimePicker.heightProperty());
        HBox preferredTimeInputGroup = new HBox(preferredTimePicker, addPreferredTimeButton);
        preferredTimeInputGroup.setAlignment(Pos.CENTER_LEFT);
        addPreferredTimeButton.setOnAction(event -> {
            LocalTime time = preferredTimePicker.getTime();
            if (time != null && !preferredTimesItems.contains(time)) {
                preferredTimesItems.add(time);
                preferredTimesItems.sort(null);
                refreshPreferredTimeChips(preferredTimesTiles, preferredTimesItems, preferredTimeInputGroup);
            }
        });
        // An event filter (not setOnKeyPressed) so this runs *before* and
        // consumes the event ahead of TimePicker's own KEY_PRESSED handler,
        // which would otherwise still fire its (now hidden, but F4-reachable)
        // clock-face popup on Enter too.
        preferredTimePicker.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addPreferredTimeButton.fire();
                event.consume();
            }
        });
        refreshPreferredTimeChips(preferredTimesTiles, preferredTimesItems, preferredTimeInputGroup);
        CheckBox experiencedCheck = new CheckBox();
        experiencedCheck.setSelected(server.experienced());
        CheckBox activeCheck = new CheckBox();
        activeCheck.setSelected(server.active());

        // Created here (not inline in the grid section below, where they
        // used to be) so the dirty-recompute closures below - which run from
        // listeners attached well before the grid is built - can already
        // close over them.
        Label qualificationsLabel = new Label(Localization.lang("Qualifications"));
        Label preferredTimesLabel = new Label(Localization.lang("Preferred times"));
        Label unavailabilityLabel = new Label(Localization.lang("Unavailable periods"));

        // Row height scales with the app font size (keeps rows compact and
        // legible when the user changes the font in Settings).
        DoubleBinding cellSize = Bindings.createDoubleBinding(
                () -> uiPreferences.fontSizeProperty().get() * CELL_SIZE_FONT_FACTOR,
                uiPreferences.fontSizeProperty());

        // Deferred pushLive reference: the checkbox properties (and their
        // listeners) are created before pushLive itself can exist, and the
        // cell factory keeps creating properties lazily for roles added
        // while this editor is open - each must push edits live too, so the
        // listener attaches inside the shared factory function.
        Runnable[] pushLiveHolder = new Runnable[1];
        Map<String, BooleanProperty> qualificationSelected = new HashMap<>();
        // A single label covers the whole checklist - unlike markDirtyOnChange
        // (one property, one label), this must re-diff the *entire* live
        // qualification set against the baseline on every checkbox toggle,
        // since any one of them flipping can change whether the set as a
        // whole still matches what's on disk.
        Runnable recomputeQualificationsChanged = () -> {
            Set<String> liveQualifications = new HashSet<>();
            qualificationSelected.forEach((roleId, ticked) -> {
                if (ticked.get()) {
                    liveQualifications.add(roleId);
                }
            });
            setFieldChanged(qualificationsLabel, !liveQualifications.equals(baselineSupplier.get().qualifications()));
        };
        Function<String, BooleanProperty> qualificationProperty = roleId -> {
            SimpleBooleanProperty ticked = new SimpleBooleanProperty(server.qualifications().contains(roleId));
            ticked.addListener((obs, oldValue, newValue) -> {
                pushLiveHolder[0].run();
                recomputeQualificationsChanged.run();
            });
            return ticked;
        };
        // Seed eagerly for every current role: pushLive rebuilds the
        // qualification set from this map, so a checked role whose cell was
        // never rendered (scrolled out of view) must still be represented.
        for (Role role : roleStore.items()) {
            qualificationSelected.computeIfAbsent(role.id(), qualificationProperty);
        }
        recomputeQualificationsChanged.run();
        // The store's own live list - not a copy - so roles created, renamed
        // or deleted anywhere (even unsaved) appear here immediately.
        ListView<Role> qualificationsList = new ListView<>(roleStore.items());
        qualificationsList.fixedCellSizeProperty().bind(cellSize);
        qualificationsList.setPrefHeight(150);
        qualificationsList.setCellFactory(CheckBoxListCell.forListView(
                role -> qualificationSelected.computeIfAbsent(role.id(), qualificationProperty),
                new StringConverter<>() {
                    @Override
                    public String toString(@Nullable Role role) {
                        return role == null ? "" : role.name();
                    }

                    @Override
                    public @Nullable Role fromString(@Nullable String string) {
                        return null;
                    }
                }));

        ListView<UnavailabilityPeriod> unavailabilityList = new ListView<>(
                FXCollections.observableArrayList(server.unavailabilities()));
        unavailabilityList.fixedCellSizeProperty().bind(cellSize);
        unavailabilityList.setPrefHeight(110);
        unavailabilityList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UnavailabilityPeriod period, boolean empty) {
                super.updateItem(period, empty);
                setText(empty || period == null ? null : period.start() + " - " + period.end());
            }
        });
        CalendarPicker periodFromPicker = CalendarPickers.create();
        periodFromPicker.setPromptText(Localization.lang("From"));
        CalendarPicker periodToPicker = CalendarPickers.create();
        periodToPicker.setPromptText(Localization.lang("To"));
        Button addPeriodButton = new Button(Localization.lang("Add"));
        addPeriodButton.setOnAction(event -> {
            LocalDate from = periodFromPicker.getValue();
            LocalDate to = periodToPicker.getValue();
            if (from == null || to == null || to.isBefore(from)) {
                return;
            }
            unavailabilityList.getItems().add(new UnavailabilityPeriod(from, to));
            periodFromPicker.setValue(null);
            periodToPicker.setValue(null);
        });
        Button removePeriodButton = new Button(Localization.lang("Remove"));
        removePeriodButton.setOnAction(event -> {
            UnavailabilityPeriod period = unavailabilityList.getSelectionModel().getSelectedItem();
            if (period != null) {
                unavailabilityList.getItems().remove(period);
            }
        });

        // Same "whole list vs baseline" reasoning as recomputeQualificationsChanged
        // above - compared as sets, order isn't semantically significant for
        // either field.
        Runnable recomputePreferredTimesChanged = () -> setFieldChanged(preferredTimesLabel,
                !new HashSet<>(preferredTimesItems).equals(baselineSupplier.get().preferredTimes()));
        Runnable recomputeUnavailabilityChanged = () -> setFieldChanged(unavailabilityLabel,
                !new HashSet<>(unavailabilityList.getItems()).equals(new HashSet<>(baselineSupplier.get().unavailabilities())));
        recomputePreferredTimesChanged.run();
        recomputeUnavailabilityChanged.run();

        // Guards every control's change listener against firing while the
        // refresh callback below is pushing an externally-changed value into
        // the controls - without it, a refresh's programmatic set can
        // trigger a *second*, reentrant items.set() on the shared store list
        // while an outer one is still unwinding through its own listener
        // chain, corrupting JavaFX's internal ListChangeBuilder (observed as
        // an UnmodifiableList.add crash deep in ListChangeBuilder.nextRemove
        // - see RolesModule for the same fix).
        boolean[] suppressPushLive = new boolean[1];

        // Facade write path: every control's change listener below rebuilds a
        // fresh Server from current control values and pushes it straight into
        // the table's live state (no editor-owned Save button).
        Runnable pushLive = () -> {
            if (suppressPushLive[0]) {
                return;
            }
            Set<String> qualifications = new HashSet<>();
            qualificationSelected.forEach((roleId, ticked) -> {
                if (ticked.get()) {
                    qualifications.add(roleId);
                }
            });
            String familyId = familyIdField.getText().strip();
            updateLive(new Server(
                    server.id(),
                    firstNameField.getText().strip(),
                    lastNameField.getText().strip(),
                    contactField.getText().strip(),
                    birthDatePicker.getValue(),
                    familyId.isEmpty() ? null : familyId,
                    qualifications,
                    new ArrayList<>(unavailabilityList.getItems()),
                    new HashSet<>(preferredTimesItems),
                    experiencedCheck.isSelected(),
                    activeCheck.isSelected()));
        };
        pushLiveHolder[0] = pushLive;
        firstNameField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        lastNameField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        contactField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        birthDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        familyIdField.textProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        experiencedCheck.selectedProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        activeCheck.selectedProperty().addListener((obs, oldValue, newValue) -> pushLive.run());
        preferredTimesItems.addListener((ListChangeListener<LocalTime>) change -> {
            pushLive.run();
            recomputePreferredTimesChanged.run();
        });
        unavailabilityList.getItems().addListener((ListChangeListener<UnavailabilityPeriod>) change -> {
            pushLive.run();
            recomputeUnavailabilityChanged.run();
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        Label firstNameLabel = new Label(Localization.lang("First name"));
        Label lastNameLabel = new Label(Localization.lang("Last name"));
        Label contactLabel = new Label(Localization.lang("Contact"));
        Label birthDateLabel = new Label(Localization.lang("Birth date"));
        Label familyLabel = new Label(Localization.lang("Family"));
        Label experiencedLabel = new Label(Localization.lang("Experienced"));
        Label activeLabel = new Label(Localization.lang("Active"));

        int row = 0;
        grid.add(firstNameLabel, 0, row);
        grid.add(firstNameField, 1, row++);
        grid.add(lastNameLabel, 0, row);
        grid.add(lastNameField, 1, row++);
        grid.add(contactLabel, 0, row);
        grid.add(contactField, 1, row++);
        grid.add(birthDateLabel, 0, row);
        grid.add(birthDatePicker, 1, row++);
        grid.add(familyLabel, 0, row);
        grid.add(familyIdField, 1, row++);
        GridPane.setValignment(preferredTimesLabel, VPos.TOP);
        grid.add(preferredTimesLabel, 0, row);
        grid.add(preferredTimesTiles, 1, row++);
        grid.add(experiencedLabel, 0, row);
        grid.add(experiencedCheck, 1, row++);
        grid.add(activeLabel, 0, row);
        grid.add(activeCheck, 1, row++);

        GridPane.setValignment(qualificationsLabel, VPos.TOP);
        // The list's first row isn't flush with its own top edge (border +
        // cell padding), so a plain top-aligned label sits a few pixels
        // above it - nudge the label down to match.
        qualificationsLabel.setPadding(new Insets(4, 0, 0, 0));
        grid.add(qualificationsLabel, 0, row);
        GridPane.setVgrow(qualificationsList, Priority.ALWAYS);
        grid.add(qualificationsList, 1, row++);

        GridPane.setValignment(unavailabilityLabel, VPos.TOP);
        unavailabilityLabel.setPadding(new Insets(4, 0, 0, 0));
        grid.add(unavailabilityLabel, 0, row);
        // FlowPane, not HBox - From/To/Add/Remove wrap onto a second line
        // instead of forcing the whole editor pane to a wide minimum width
        // when the window is narrow. Unlike Controls (ListView, TextField,
        // ...), plain layout panes have no default "-fx-max-width: infinity"
        // - without explicit max widths, neither the VBox nor the FlowPane
        // grows past its own computed preferred width, so GridPane has
        // nothing wider to give them and the FlowPane wraps at a narrow
        // width regardless of how much column space is actually free.
        // Growing both, then binding prefWrapLength to the now-genuinely-
        // stretched VBox width, makes wrapping track the real available space.
        FlowPane periodControls = new FlowPane(8, 8,
                periodFromPicker, periodToPicker, addPeriodButton, removePeriodButton);
        periodControls.setMaxWidth(Double.MAX_VALUE);
        VBox unavailabilityBox = new VBox(8, unavailabilityList, periodControls);
        unavailabilityBox.setMaxWidth(Double.MAX_VALUE);
        periodControls.prefWrapLengthProperty().bind(unavailabilityBox.widthProperty());
        GridPane.setVgrow(unavailabilityBox, Priority.ALWAYS);
        grid.add(unavailabilityBox, 1, row++);

        VBox content = new VBox(10, grid);
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        markDirtyOnChange(firstNameField.textProperty(), () -> baselineSupplier.get().firstName(), firstNameLabel);
        markDirtyOnChange(lastNameField.textProperty(), () -> baselineSupplier.get().lastName(), lastNameLabel);
        markDirtyOnChange(contactField.textProperty(), () -> baselineSupplier.get().contact(), contactLabel);
        markDirtyOnChange(birthDatePicker.valueProperty(), () -> baselineSupplier.get().birthDate(), birthDateLabel);
        markDirtyOnChange(familyIdField.textProperty(),
                () -> Objects.requireNonNullElse(baselineSupplier.get().familyId(), ""), familyLabel);
        markDirtyOnChange(experiencedCheck.selectedProperty(), () -> baselineSupplier.get().experienced(), experiencedLabel);
        markDirtyOnChange(activeCheck.selectedProperty(), () -> baselineSupplier.get().active(), activeLabel);

        // refresh: the row's value changed externally (e.g. a Load reverted
        // this server) - push every field back to the new value in place.
        return EditorBinding.of(content, updated -> {
            suppressPushLive[0] = true;
            try {
                firstNameField.setText(updated.firstName());
                lastNameField.setText(updated.lastName());
                contactField.setText(updated.contact());
                birthDatePicker.setValue(updated.birthDate());
                String updatedFamilyId = updated.familyId();
                familyIdField.setSelectedItem(updatedFamilyId == null ? "" : updatedFamilyId);
                qualificationSelected.forEach((roleId, ticked) -> ticked.set(updated.qualifications().contains(roleId)));
                preferredTimesItems.setAll(updated.preferredTimes().stream().sorted().toList());
                refreshPreferredTimeChips(preferredTimesTiles, preferredTimesItems, preferredTimeInputGroup);
                unavailabilityList.getItems().setAll(updated.unavailabilities());
                experiencedCheck.setSelected(updated.experienced());
                activeCheck.setSelected(updated.active());
            } finally {
                suppressPushLive[0] = false;
            }
            // None of the sets above necessarily changed what a control
            // displays (a Save all moves the baseline, not the live value),
            // so their own listeners may not have fired - recompute
            // explicitly rather than relying on one.
            recomputeFieldChanged(firstNameField.textProperty(), () -> baselineSupplier.get().firstName(), firstNameLabel);
            recomputeFieldChanged(lastNameField.textProperty(), () -> baselineSupplier.get().lastName(), lastNameLabel);
            recomputeFieldChanged(contactField.textProperty(), () -> baselineSupplier.get().contact(), contactLabel);
            recomputeFieldChanged(birthDatePicker.valueProperty(), () -> baselineSupplier.get().birthDate(), birthDateLabel);
            recomputeFieldChanged(familyIdField.textProperty(),
                    () -> Objects.requireNonNullElse(baselineSupplier.get().familyId(), ""), familyLabel);
            recomputeFieldChanged(experiencedCheck.selectedProperty(), () -> baselineSupplier.get().experienced(), experiencedLabel);
            recomputeFieldChanged(activeCheck.selectedProperty(), () -> baselineSupplier.get().active(), activeLabel);
            recomputeQualificationsChanged.run();
            recomputePreferredTimesChanged.run();
            recomputeUnavailabilityChanged.run();
        });
    }

    /// Rebuilds {@code flow} from {@code times} - one closable {@link ChipView}
    /// per entry, plus {@code inputGroup} (the time picker + add button) as the
    /// trailing entry, so both chips and the input group share one
    /// {@link FlowPane}. A {@code FlowPane} (not a {@code TilePane}) so each
    /// chip stays sized to its own text instead of stretching to match the
    /// wider input group's cell width.
    private void refreshPreferredTimeChips(FlowPane flow, ObservableList<LocalTime> times, Node inputGroup) {
        List<Node> children = new ArrayList<>(times.stream()
                .map(time -> (Node) buildPreferredTimeChip(time, times, flow, inputGroup))
                .toList());
        children.add(inputGroup);
        flow.getChildren().setAll(children);
    }

    private ChipView<LocalTime> buildPreferredTimeChip(LocalTime time, ObservableList<LocalTime> times,
                                                        FlowPane flow, Node inputGroup) {
        ChipView<LocalTime> chip = new ChipView<>();
        chip.setValue(time);
        chip.setText(TIME_FORMAT.format(time));
        chip.setOnClose(value -> {
            times.remove(value);
            refreshPreferredTimeChips(flow, times, inputGroup);
        });
        return chip;
    }

    /// Free-text field with autocomplete over family ids already used by other
    /// servers, so a sibling gets linked to an existing family instead of a
    /// typo'd new one. A brand-new id (no match) is still accepted as-is via
    /// {@link SearchField#setNewItemProducer} - otherwise {@code commit()}
    /// would silently clear whatever the user typed.
    private SearchField<String> buildFamilyIdField(@Nullable String familyId) {
        SearchField<String> field = new SearchField<>();
        SearchFields.applyTheme(field);
        field.setPromptText(Localization.lang("Family"));
        field.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable String value) {
                return value == null ? "" : value;
            }

            @Override
            public String fromString(@Nullable String string) {
                return string == null ? "" : string;
            }
        });
        field.setMatcher((item, searchText) -> item.toLowerCase(Locale.ROOT).startsWith(searchText.toLowerCase(Locale.ROOT)));
        field.setSuggestionProvider(request -> viewModel.familyIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(request.getUserText().toLowerCase(Locale.ROOT)))
                .toList());
        field.setNewItemProducer(text -> text);
        // Seed via setSelectedItem(), not setText(): the editor's text listener
        // starts a live search on every text change unless SearchField's own
        // internal "committing" guard is active, which only setSelectedItem()
        // (while the editor is still blank) goes through - setText() here would
        // pop the suggestion popup open the instant the editor is built.
        if (familyId != null && !familyId.isBlank()) {
            field.setSelectedItem(familyId);
        }
        return field;
    }
}
