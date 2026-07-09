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
import java.util.Set;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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

import atlantafx.base.theme.Styles;
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

/**
 * Altar server roster module: personal details, role qualifications and
 * unavailability periods (both part of the {@link Server} model).
 */
public class ServersModule extends CrudModule<Server> {

    // Checkbox list row height as a multiple of the app font size.
    private static final double CELL_SIZE_FONT_FACTOR = 2.0;
    private static final double EDITOR_MIN_HEIGHT = 520;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ServersViewModel viewModel;
    private final UiPreferences uiPreferences;

    public ServersModule(String name, ServerRepository serverRepository, RoleRepository roleRepository,
                         UiPreferences uiPreferences) {
        super(name, "mdi2a-account-group");
        this.viewModel = new ServersViewModel(serverRepository, roleRepository);
        this.uiPreferences = uiPreferences;

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
    protected Server createStub() {
        return viewModel.createStub();
    }

    @Override
    protected List<Server> loadAll() {
        return viewModel.findAll();
    }

    @Override
    protected void persist(Server server) {
        viewModel.save(server);
    }

    @Override
    protected void delete(Server server) {
        viewModel.delete(server);
    }

    @Override
    protected Object identity(Server server) {
        return server.id();
    }

    @Override
    protected Node buildEditor(Server server) {
        TextField firstNameField = new TextField(server.firstName());
        TextField lastNameField = new TextField(server.lastName());
        TextField contactField = new TextField(server.contact());
        CalendarPicker birthDatePicker = CalendarPickers.create();
        birthDatePicker.setValue(server.birthDate());
        SearchField<String> familyIdField = buildFamilyIdField(server.familyId());
        ObservableList<LocalTime> preferredTimesItems = FXCollections.observableArrayList(
                server.preferredTimes().stream().sorted().toList());
        FlowPane preferredTimesTiles = new FlowPane(6, 6);
        TimePicker preferredTimePicker = TimePickers.create(false);
        preferredTimePicker.getStyleClass().add(Styles.LEFT_PILL);
        Button addPreferredTimeButton = new Button(null, new FontIcon("mdi2p-plus"));
        addPreferredTimeButton.getStyleClass().add(Styles.RIGHT_PILL);
        // A plain Button's own default padding computes a taller natural height
        // than the TimePicker's - binding prefHeight to the picker's height alone
        // (tried first) didn't fix it because minHeight, left at its own larger
        // computed default, is what actually floors the final layout height
        // (JavaFX clamps to at least minHeight). Pinning minHeight to track
        // prefHeight lets it actually shrink to match, flushing the shared pill
        // border top and bottom.
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

        // Row height scales with the app font size (keeps rows compact and
        // legible when the user changes the font in Settings).
        DoubleBinding cellSize = Bindings.createDoubleBinding(
                () -> uiPreferences.fontSizeProperty().get() * CELL_SIZE_FONT_FACTOR,
                uiPreferences.fontSizeProperty());

        Map<String, BooleanProperty> qualificationSelected = new HashMap<>();
        ObservableList<Role> roles = FXCollections.observableArrayList(viewModel.findAllRoles());
        for (Role role : roles) {
            qualificationSelected.put(role.id(),
                    new SimpleBooleanProperty(server.qualifications().contains(role.id())));
        }
        ListView<Role> qualificationsList = new ListView<>(roles);
        qualificationsList.fixedCellSizeProperty().bind(cellSize);
        qualificationsList.setPrefHeight(150);
        qualificationsList.setCellFactory(CheckBoxListCell.forListView(
                role -> qualificationSelected.computeIfAbsent(role.id(), id -> new SimpleBooleanProperty()),
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

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.add(new Label(Localization.lang("First name")), 0, row);
        grid.add(firstNameField, 1, row++);
        grid.add(new Label(Localization.lang("Last name")), 0, row);
        grid.add(lastNameField, 1, row++);
        grid.add(new Label(Localization.lang("Contact")), 0, row);
        grid.add(contactField, 1, row++);
        grid.add(new Label(Localization.lang("Birth date")), 0, row);
        grid.add(birthDatePicker, 1, row++);
        grid.add(new Label(Localization.lang("Family")), 0, row);
        grid.add(familyIdField, 1, row++);
        Label preferredTimesLabel = new Label(Localization.lang("Preferred times"));
        GridPane.setValignment(preferredTimesLabel, VPos.TOP);
        grid.add(preferredTimesLabel, 0, row);
        grid.add(preferredTimesTiles, 1, row++);
        grid.add(new Label(Localization.lang("Experienced")), 0, row);
        grid.add(experiencedCheck, 1, row++);
        grid.add(new Label(Localization.lang("Active")), 0, row);
        grid.add(activeCheck, 1, row++);

        Label qualificationsLabel = new Label(Localization.lang("Qualifications"));
        GridPane.setValignment(qualificationsLabel, VPos.TOP);
        // The list's first row isn't flush with its own top edge (border +
        // cell padding), so a plain top-aligned label sits a few pixels
        // above it - nudge the label down to match.
        qualificationsLabel.setPadding(new Insets(4, 0, 0, 0));
        grid.add(qualificationsLabel, 0, row);
        GridPane.setVgrow(qualificationsList, Priority.ALWAYS);
        grid.add(qualificationsList, 1, row++);

        Label unavailabilityLabel = new Label(Localization.lang("Unavailable periods"));
        GridPane.setValignment(unavailabilityLabel, VPos.TOP);
        unavailabilityLabel.setPadding(new Insets(4, 0, 0, 0));
        grid.add(unavailabilityLabel, 0, row);
        VBox unavailabilityBox = new VBox(8, unavailabilityList,
                new HBox(8, periodFromPicker, periodToPicker, addPeriodButton, removePeriodButton));
        GridPane.setVgrow(unavailabilityBox, Priority.ALWAYS);
        grid.add(unavailabilityBox, 1, row++);

        Button saveButton = new Button(Localization.lang("Save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            String firstName = firstNameField.getText().strip();
            String lastName = lastNameField.getText().strip();
            if (firstName.isEmpty() && lastName.isEmpty()) {
                return;
            }
            Set<String> qualifications = new HashSet<>();
            qualificationSelected.forEach((roleId, ticked) -> {
                if (ticked.get()) {
                    qualifications.add(roleId);
                }
            });
            String familyId = familyIdField.getText().strip();
            save(new Server(
                    server.id(),
                    firstName,
                    lastName,
                    contactField.getText().strip(),
                    birthDatePicker.getValue(),
                    familyId.isEmpty() ? null : familyId,
                    qualifications,
                    new ArrayList<>(unavailabilityList.getItems()),
                    new HashSet<>(preferredTimesItems),
                    experiencedCheck.isSelected(),
                    activeCheck.isSelected()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }

    /**
     * Rebuilds {@code flow} from {@code times} - one closable {@link ChipView}
     * per entry, plus {@code inputGroup} (the time picker + add button) as the
     * trailing entry, so both chips and the input group share one
     * {@link FlowPane}. A {@code FlowPane} (not a {@code TilePane}) so each
     * chip stays sized to its own text instead of stretching to match the
     * wider input group's cell width.
     */
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

    /**
     * Free-text field with autocomplete over family ids already used by other
     * servers, so a sibling gets linked to an existing family instead of a
     * typo'd new one. A brand-new id (no match) is still accepted as-is via
     * {@link SearchField#setNewItemProducer} - otherwise {@code commit()}
     * would silently clear whatever the user typed.
     */
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
