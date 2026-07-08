package org.mindis.gui.modules;

import atlantafx.base.controls.Tile;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.preferences.AppLanguage;
import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.gui.preferences.PreferenceControls;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.theme.AccentColorSelector;
import org.mindis.workbench.WorkbenchModule;

/**
 * Settings screen. Each preferences group is a {@link TitledPane}; inside,
 * every setting is an AtlantaFX {@link Tile} (title + description on the left,
 * its control in the action slot on the right), stacked into a settings-list
 * look. All controls bind bidirectionally to {@link UiPreferences}.
 */
public class SettingsModule extends WorkbenchModule {

    private final UiPreferences uiPreferences;

    public SettingsModule(String name, UiPreferences uiPreferences) {
        super(name, "mdi2c-cog");
        this.uiPreferences = uiPreferences;
    }

    @Override
    public Node activate() {
        VBox content = new VBox(24, appearancePane(), solverPane());
        content.setMaxWidth(520);
        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.TOP_CENTER);
        wrapper.setPadding(new javafx.geometry.Insets(24));

        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** UiPreferences group: language, theme, accent and font. */
    private TitledPane appearancePane() {
        ComboBox<AppLanguage> languageBox =
                PreferenceControls.choiceBox(AppLanguage.values(), uiPreferences.languageProperty());

        // Theme dropdown is disabled while the app follows the OS light/dark
        // scheme; the follow toggle drives that. Both share one tile.
        ComboBox<MinDisPreferences.Theme> themeBox =
                PreferenceControls.choiceBox(MinDisPreferences.Theme.values(), uiPreferences.themeProperty());
        themeBox.disableProperty().bind(uiPreferences.followSystemThemeProperty());
        CheckBox followSystemCheck = new CheckBox(Localization.lang("Follow system theme"));
        followSystemCheck.selectedProperty().bindBidirectional(uiPreferences.followSystemThemeProperty());

        AccentColorSelector accentSelector = new AccentColorSelector(uiPreferences.accentColorProperty());

        // Font family + size share one tile, controls side by side.
        ComboBox<String> fontFamilyBox = fontFamilyBox();
        fontFamilyBox.setPrefWidth(150);
        Spinner<Integer> fontSizeSpinner = new Spinner<>(
                MinDisPreferences.MIN_FONT_SIZE, MinDisPreferences.MAX_FONT_SIZE,
                uiPreferences.fontSizeProperty().get());
        fontSizeSpinner.getValueFactory().valueProperty()
                .bindBidirectional(uiPreferences.fontSizeProperty());
        fontSizeSpinner.setPrefWidth(75);

        VBox tiles = new VBox(
                tile(Localization.lang("Language"), Localization.lang("Interface language"), languageBox),
                tile(Localization.lang("Theme"), Localization.lang("Light or dark or follow the system"),
                        new HBox(8, themeBox, followSystemCheck)),
                tile(Localization.lang("Accent color"), Localization.lang("Highlight color across the app"),
                        accentSelector),
                tile(Localization.lang("Font"), Localization.lang("Application font family and size"),
                        new HBox(8, fontFamilyBox, fontSizeSpinner)));
        return groupPane(Localization.lang("Appearance"), tiles);
    }

    /** MinDisPreferences group: solver time budget and constraint weights. */
    private TitledPane solverPane() {
        Spinner<Integer> solverSecondsSpinner = new Spinner<>(5, 600,
                uiPreferences.solverSecondsLimitProperty().get(), 5);
        solverSecondsSpinner.setEditable(true);
        solverSecondsSpinner.getValueFactory().valueProperty()
                .bindBidirectional(uiPreferences.solverSecondsLimitProperty());
        solverSecondsSpinner.setPrefWidth(110);

        VBox tiles = new VBox(tile(Localization.lang("Solver time limit (seconds)"), null, solverSecondsSpinner));
        for (String constraintName : MinDisConstraintProvider.tunableSoftConstraints()) {
            Spinner<Integer> weightSpinner = new Spinner<>(0, 20,
                    uiPreferences.softWeightProperty(constraintName).get());
            weightSpinner.getValueFactory().valueProperty()
                    .bindBidirectional(uiPreferences.softWeightProperty(constraintName));
            weightSpinner.setPrefWidth(110);
            tiles.getChildren().add(tile(Localization.lang(constraintName),
                    Localization.lang("Constraint weight"), weightSpinner));
        }
        return groupPane(Localization.lang("Solver"), tiles);
    }

    private ComboBox<String> fontFamilyBox() {
        // "Default" sentinel first, then every font family the platform offers.
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList());
        box.getItems().add(MinDisPreferences.DEFAULT_FONT_FAMILY);
        box.getItems().addAll(Font.getFamilies());
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(String family) {
                return MinDisPreferences.DEFAULT_FONT_FAMILY.equals(family)
                        ? Localization.lang("Default") : family;
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });
        box.valueProperty().bindBidirectional(uiPreferences.fontFamilyProperty());
        return box;
    }

    private Tile tile(String title, @Nullable String description, Node action) {
        Tile tile = new Tile(title, description);
        tile.setAction(action);
        return tile;
    }

    private TitledPane groupPane(String title, VBox tiles) {
        TitledPane pane = new TitledPane(title, tiles);
        pane.setCollapsible(false);
        return pane;
    }
}
