package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
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
import org.mindis.gui.util.DateTimes;
import org.mindis.gui.util.SearchFields;
import org.mindis.gui.util.TimePickers;
import org.mindis.gui.shell.CrudModule;
import org.mindis.gui.shell.EditorForm;
import org.mindis.gui.shell.ShellOverlays;
import org.mindis.gui.data.LiveStore;

/// Altar server roster module: personal details, role qualifications and
/// unavailability periods (both part of the [Server] model). The
/// qualifications checklist binds to the shared live role list, so roles
/// created or edited (even unsaved) in the Roles module appear immediately.
public final class ServersModule extends CrudModule<Server> {

    // Checkbox list row height as a multiple of the app font size.
    private static final double CELL_SIZE_FONT_FACTOR = 2.0;
    private static final double EDITOR_MIN_HEIGHT = 520;

    private final ServersViewModel viewModel;
    private final UiPreferences uiPreferences;
    private final LiveStore<Role> roleStore;
    // Repaints the table when the shared role list changes, so an unsaved
    // role rename shows in the Qualifications column immediately. A field so
    // dispose() can detach it from the module-outliving store list.
    private final ListChangeListener<Role> roleChangeListener = change -> table().refresh();

    public ServersModule(String name, LiveStore<Server> serverStore, LiveStore<Role> roleStore,
                         ServerRepository serverRepository, RoleRepository roleRepository,
                         UiPreferences uiPreferences, ShellOverlays overlays) {
        super(name, "mdi2a-account-group", serverStore, overlays);
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

        addStandardToolbar(new ServerCsvMapper(roleRepository));
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
        // The scalar rows below are declared once each on the form; the
        // collection-backed ones (qualifications, preferred times, unavailable
        // periods) are sections, because one label spanning a whole list has
        // to re-diff the entire live set against the baseline rather than
        // watch a single property.
        EditorForm<Server> form = editorForm(server);

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

        Map<String, BooleanProperty> qualificationSelected = new HashMap<>();
        Function<String, BooleanProperty> qualificationProperty = roleId -> {
            SimpleBooleanProperty ticked = new SimpleBooleanProperty(server.qualifications().contains(roleId));
            // Reported through the form: the write-through callback reads
            // every control, so it does not exist yet when the first checkbox
            // property is built (and the cell factory keeps building more for
            // roles added while this editor is open).
            ticked.addListener((obs, oldValue, newValue) -> form.edited());
            return ticked;
        };
        // Seed eagerly for every current role: the write-through rebuilds the
        // qualification set from this map, so a checked role whose cell was
        // never rendered (scrolled out of view) must still be represented.
        for (Role role : roleStore.items()) {
            qualificationSelected.computeIfAbsent(role.id(), qualificationProperty);
        }

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

        form.field(Localization.lang("First name"), firstNameField, firstNameField.textProperty(),
                Server::firstName, firstNameField::setText);
        form.field(Localization.lang("Last name"), lastNameField, lastNameField.textProperty(),
                Server::lastName, lastNameField::setText);
        form.field(Localization.lang("Contact"), contactField, contactField.textProperty(),
                Server::contact, contactField::setText);
        form.field(Localization.lang("Birth date"), birthDatePicker, birthDatePicker.valueProperty(),
                Server::birthDate, birthDatePicker::setValue);
        form.field(Localization.lang("Family"), familyIdField, familyIdField.textProperty(),
                candidate -> Objects.requireNonNullElse(candidate.familyId(), ""),
                familyIdField::setSelectedItem);
        form.section(Localization.lang("Preferred times"), preferredTimesTiles,
                label -> setFieldChanged(label,
                        !new HashSet<>(preferredTimesItems).equals(form.baseline().get().preferredTimes())),
                updated -> {
                    preferredTimesItems.setAll(updated.preferredTimes().stream().sorted().toList());
                    refreshPreferredTimeChips(preferredTimesTiles, preferredTimesItems, preferredTimeInputGroup);
                }).topAligned();
        form.field(Localization.lang("Experienced"), experiencedCheck, experiencedCheck.selectedProperty(),
                Server::experienced, experiencedCheck::setSelected);
        form.field(Localization.lang("Active"), activeCheck, activeCheck.selectedProperty(),
                Server::active, activeCheck::setSelected);
        form.section(Localization.lang("Qualifications"), qualificationsList,
                label -> {
                    Set<String> live = new HashSet<>();
                    qualificationSelected.forEach((roleId, ticked) -> {
                        if (ticked.get()) {
                            live.add(roleId);
                        }
                    });
                    setFieldChanged(label, !live.equals(form.baseline().get().qualifications()));
                },
                updated -> qualificationSelected.forEach(
                        (roleId, ticked) -> ticked.set(updated.qualifications().contains(roleId))))
                .listAligned().growing();
        // FlowPane, not HBox - From/To/Add/Remove wrap onto a second line
        // instead of forcing the whole editor pane to a wide minimum width
        // when the window is narrow. Unlike Controls, plain layout panes have
        // no default "-fx-max-width: infinity", so without explicit max widths
        // neither the VBox nor the FlowPane grows past its own preferred width
        // and the FlowPane wraps narrow regardless of free column space.
        FlowPane periodControls = new FlowPane(8, 8,
                periodFromPicker, periodToPicker, addPeriodButton, removePeriodButton);
        periodControls.setMaxWidth(Double.MAX_VALUE);
        VBox unavailabilityBox = new VBox(8, unavailabilityList, periodControls);
        unavailabilityBox.setMaxWidth(Double.MAX_VALUE);
        periodControls.prefWrapLengthProperty().bind(unavailabilityBox.widthProperty());
        form.section(Localization.lang("Unavailable periods"), unavailabilityBox,
                label -> setFieldChanged(label, !new HashSet<>(unavailabilityList.getItems())
                        .equals(new HashSet<>(form.baseline().get().unavailabilities()))),
                updated -> unavailabilityList.getItems().setAll(updated.unavailabilities()))
                .listAligned().growing();

        // Facade write path: rebuilds a fresh Server from the current control
        // values and pushes it straight into the table's live state (no
        // editor-owned Save button).
        form.onEdit(() -> {
            if (isSuppressingLiveUpdates()) {
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
        });
        // The two list-backed sections mutate their lists directly rather than
        // through a watched property, so they report edits themselves.
        preferredTimesItems.addListener((ListChangeListener<LocalTime>) change -> form.edited());
        unavailabilityList.getItems().addListener(
                (ListChangeListener<UnavailabilityPeriod>) change -> form.edited());

        VBox content = new VBox(10, form.grid());
        content.setPadding(new Insets(12));
        content.setMinHeight(EDITOR_MIN_HEIGHT);
        // refresh: the row's value changed externally (e.g. an Open, or a
        // revert) - the form pushes every declared row back into its control.
        return EditorBinding.of(content, form.refresh());
    }

    /// Rebuilds `flow` from `times` - one closable [ChipView]
    /// per entry, plus `inputGroup` (the time picker + add button) as the
    /// trailing entry, so both chips and the input group share one
    /// [FlowPane]. A `FlowPane` (not a `TilePane`) so each
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
        chip.setText(DateTimes.time(time));
        chip.setOnClose(value -> {
            times.remove(value);
            refreshPreferredTimeChips(flow, times, inputGroup);
        });
        return chip;
    }

    /// Free-text field with autocomplete over family ids already used by other
    /// servers, so a sibling gets linked to an existing family instead of a
    /// typo'd new one. A brand-new id (no match) is still accepted as-is via
    /// [SearchField#setNewItemProducer] - otherwise `commit()`
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
