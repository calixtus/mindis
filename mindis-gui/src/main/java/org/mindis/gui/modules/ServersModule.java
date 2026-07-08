package org.mindis.gui.modules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.dlsc.gemsfx.CalendarPicker;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.RoleRepository;
import org.jspecify.annotations.Nullable;

import org.mindis.core.persistence.ServerRepository;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.util.CalendarPickers;
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
    }

    @Override
    protected String newButtonLabel() {
        return Localization.lang("New");
    }

    @Override
    protected String deleteButtonLabel() {
        return Localization.lang("Delete");
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
    protected CsvRowMapper<Server> csvMapper() {
        return CsvRowMapper.of(viewModel::csvHeader, viewModel::toCsvRow, viewModel::fromCsvRow);
    }

    @Override
    protected String exportButtonLabel() {
        return Localization.lang("Export");
    }

    @Override
    protected String importButtonLabel() {
        return Localization.lang("Import");
    }

    @Override
    protected String importSummary(int imported, int total) {
        return Localization.lang("%0 of %1 rows imported", imported, total);
    }

    @Override
    protected Node buildEditor(Server server) {
        TextField firstNameField = new TextField(server.firstName());
        TextField lastNameField = new TextField(server.lastName());
        TextField contactField = new TextField(server.contact());
        CalendarPicker birthDatePicker = CalendarPickers.create();
        birthDatePicker.setValue(server.birthDate());
        TextField familyIdField = new TextField(server.familyId() == null ? "" : server.familyId());
        TextField preferredTimesField = new TextField(viewModel.formatPreferredTimes(server.preferredTimes()));
        preferredTimesField.setPromptText("10:00, 18:30");
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
        grid.add(new Label(Localization.lang("Preferred times")), 0, row);
        grid.add(preferredTimesField, 1, row++);
        grid.add(new Label(Localization.lang("Experienced")), 0, row);
        grid.add(experiencedCheck, 1, row++);
        grid.add(new Label(Localization.lang("Active")), 0, row);
        grid.add(activeCheck, 1, row++);

        Label qualificationsLabel = new Label(Localization.lang("Qualifications"));
        GridPane.setValignment(qualificationsLabel, VPos.TOP);
        grid.add(qualificationsLabel, 0, row);
        GridPane.setVgrow(qualificationsList, Priority.ALWAYS);
        grid.add(qualificationsList, 1, row++);

        Label unavailabilityLabel = new Label(Localization.lang("Unavailable periods"));
        GridPane.setValignment(unavailabilityLabel, VPos.TOP);
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
                    viewModel.parsePreferredTimes(preferredTimesField.getText()),
                    experiencedCheck.isSelected(),
                    activeCheck.isSelected()));
        });

        VBox content = new VBox(10, grid, new HBox(saveButton));
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        return content;
    }
}
