package org.mindis.gui.modules;

import atlantafx.base.controls.Tile;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;

import java.util.Locale;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.preferences.AccentColor;
import org.mindis.core.preferences.AppLanguage;
import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.gui.preferences.PreferenceControls;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.theme.AccentColorSelector;
import org.mindis.workbench.WorkbenchModule;

/// Settings screen. Each preferences group is a {@link TitledPane} with a
/// "Reset to defaults" button in its header; inside, every setting is an
/// AtlantaFX {@link Tile} (title + description on the left, its control in
/// the action slot on the right, highlighted on hover), stacked into a
/// settings-list look. All controls bind bidirectionally to
/// {@link UiPreferences}, and stretch to use whatever width the module has
/// rather than sitting at a fixed narrow size.
public class SettingsModule extends WorkbenchModule {

    // A fixed width, not one reactively bound to the pane's own width - that
    // was tried first (clamped to a fraction of content's width) and made
    // shrinking the window noticeably laggy: ~10 controls (several of them
    // Sliders and ComboBoxes, not cheap to relayout) all recomputing and
    // reapplying their preferred width on every resize pixel, each
    // retriggering the Tile/TitledPane/VBox layout cascade above them.
    // Fixed-but-generous keeps the "wider than the old cramped 180px"
    // improvement without any resize-triggered churn at all - confirmed
    // the lag is Settings-specific (other tabs resize fine), and this is
    // the only reactive-width code unique to this pane.
    private static final double CONTROL_WIDTH = 280;

    // Below this, labels and controls would be squeezed too tight to stay
    // legible - the pane keeps this width and the ScrollPane grows a
    // horizontal scrollbar instead of compressing further.
    private static final double MIN_CONTENT_WIDTH = 560;

    private final UiPreferences uiPreferences;

    public SettingsModule(String name, UiPreferences uiPreferences) {
        super(name, "mdi2c-cog");
        this.uiPreferences = uiPreferences;
    }

    @Override
    public Node activate() {
        VBox content = new VBox(24, appearancePane(), solverPane());
        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.TOP_CENTER);
        wrapper.setPadding(new Insets(24));
        wrapper.setMinWidth(MIN_CONTENT_WIDTH);

        ScrollPane scroll = new ScrollPane(wrapper);
        // fitToWidth still stretches the pane to fill a wide window, but
        // never below wrapper's own min width - once the viewport gets
        // narrower than that, the pane holds its width and this scrolls
        // horizontally instead of squeezing tiles further.
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    /// UiPreferences group: language, theme, accent and font.
    private TitledPane appearancePane() {
        ComboBox<AppLanguage> languageBox =
                PreferenceControls.choiceBox(AppLanguage.values(), uiPreferences.languageProperty());
        languageBox.setPrefWidth(CONTROL_WIDTH);

        // Theme dropdown is disabled while the app follows the OS light/dark
        // scheme; the toggle in its own tile below drives that.
        ComboBox<MinDisPreferences.Theme> themeBox =
                PreferenceControls.choiceBox(MinDisPreferences.Theme.values(), uiPreferences.themeProperty());
        themeBox.disableProperty().bind(uiPreferences.followSystemThemeProperty());
        themeBox.setPrefWidth(CONTROL_WIDTH);

        ToggleSwitch followSystemToggle = new ToggleSwitch();
        followSystemToggle.selectedProperty().bindBidirectional(uiPreferences.followSystemThemeProperty());

        AccentColorSelector accentSelector = new AccentColorSelector(uiPreferences.accentColorProperty());

        // Font family + size share one tile, controls side by side - sized
        // as a row (not fontFamilyBox alone) so the row's own right edge
        // lines up with every other control above and below it.
        ComboBox<String> fontFamilyBox = fontFamilyBox();
        // Some font family names are long enough that the combo box's own
        // minimum content width, left unbounded, squeezed the spinner next
        // to it down past the point its number was visible at all (just the
        // up/down buttons) - an explicit min width on both stops either one
        // being compressed below usable, even if the row ends up a little
        // over CONTROL_WIDTH for a long font name.
        fontFamilyBox.setMinWidth(120);
        Spinner<Integer> fontSizeSpinner = new Spinner<>(
                MinDisPreferences.MIN_FONT_SIZE, MinDisPreferences.MAX_FONT_SIZE,
                uiPreferences.fontSizeProperty().get());
        fontSizeSpinner.getValueFactory().valueProperty()
                .bindBidirectional(uiPreferences.fontSizeProperty());
        fontSizeSpinner.setPrefWidth(75);
        fontSizeSpinner.setMinWidth(70);
        HBox fontRow = new HBox(8, fontFamilyBox, fontSizeSpinner);
        fontRow.setPrefWidth(CONTROL_WIDTH);
        HBox.setHgrow(fontFamilyBox, Priority.ALWAYS);

        VBox tiles = new VBox(
                tile(Localization.lang("Language"), Localization.lang("Interface language"), languageBox),
                tile(Localization.lang("Theme"), Localization.lang("Light or dark or follow the system"), themeBox),
                tile(Localization.lang("Follow system theme"),
                        Localization.lang("Use the OS light/dark setting instead of the Theme dropdown"),
                        followSystemToggle),
                tile(Localization.lang("Accent color"), Localization.lang("Highlight color across the app"),
                        accentSelector),
                tile(Localization.lang("Font"), Localization.lang("Application font family and size"), fontRow));
        return groupPane(Localization.lang("Appearance"), tiles, this::resetAppearanceToDefaults);
    }

    /// MinDisPreferences group: solver time budget and constraint weights.
    private TitledPane solverPane() {
        Slider solverSecondsSlider = PreferenceControls.intSlider(5, 600, uiPreferences.solverSecondsLimitProperty());

        VBox tiles = new VBox(tile(Localization.lang("Solver time limit (seconds)"), null,
                sliderWithValue(solverSecondsSlider)));
        for (String constraintName : MinDisConstraintProvider.tunableSoftConstraints()) {
            Slider weightSlider = PreferenceControls.intSlider(0, 20, uiPreferences.softWeightProperty(constraintName));
            tiles.getChildren().add(tile(Localization.lang(constraintName),
                    Localization.lang("Constraint weight"), sliderWithValue(weightSlider)));
        }
        return groupPane(Localization.lang("Solver"), tiles, this::resetSolverToDefaults);
    }

    private void resetAppearanceToDefaults() {
        String systemLanguage = "de".equals(Locale.getDefault().getLanguage()) ? "de" : "en";
        uiPreferences.languageProperty().set(AppLanguage.fromTag(systemLanguage));
        uiPreferences.themeProperty().set(MinDisPreferences.Theme.LIGHT);
        uiPreferences.followSystemThemeProperty().set(false);
        uiPreferences.accentColorProperty().set(AccentColor.DEFAULT);
        uiPreferences.fontFamilyProperty().set(MinDisPreferences.DEFAULT_FONT_FAMILY);
        uiPreferences.fontSizeProperty().set(MinDisPreferences.DEFAULT_FONT_SIZE);
    }

    private void resetSolverToDefaults() {
        uiPreferences.solverSecondsLimitProperty().set(MinDisPreferences.DEFAULT_SOLVER_SECONDS);
        for (Map.Entry<String, Integer> entry : MinDisConstraintProvider.defaultSoftWeights().entrySet()) {
            uiPreferences.softWeightProperty(entry.getKey()).set(entry.getValue());
        }
    }

    /// A slider paired with an editable numeric field - typing a value and
    /// pressing Enter (or clicking away) moves the slider to match, clamped
    /// to its range; dragging the slider updates the field back, unless the
    /// field currently has focus (so it doesn't fight a value the user is
    /// mid-typing). Sized as a row (not the slider alone) so the row's right
    /// edge lines up with every other control - same fix as the font row:
    /// setting the slider itself to CONTROL_WIDTH and adding the value field
    /// on top of that made the row wider than everything else.
    private HBox sliderWithValue(Slider slider) {
        TextField valueField = new TextField(String.valueOf(Math.round(slider.getValue())));
        valueField.setPrefColumnCount(4);
        valueField.setPrefWidth(56);
        valueField.setMinWidth(50);

        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!valueField.isFocused()) {
                valueField.setText(String.valueOf(Math.round(newValue.doubleValue())));
            }
        });

        Runnable commit = () -> {
            try {
                int parsed = Integer.parseInt(valueField.getText().strip());
                int clamped = Math.clamp(parsed, (int) slider.getMin(), (int) slider.getMax());
                slider.setValue(clamped);
                valueField.setText(String.valueOf(clamped));
            } catch (NumberFormatException e) {
                valueField.setText(String.valueOf(Math.round(slider.getValue())));
            }
        };
        valueField.setOnAction(e -> commit.run());
        valueField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commit.run();
            }
        });

        HBox row = new HBox(8, slider, valueField);
        row.setPrefWidth(CONTROL_WIDTH);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
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

    /// A Tile with a hover highlight - AtlantaFX's own styling doesn't distinguish a hovered settings row otherwise.
    private Tile tile(String title, @Nullable String description, Node action) {
        Tile tile = new Tile(title, description);
        tile.setAction(action);
        tile.setOnMouseEntered(e -> tile.setStyle("-fx-background-color: -color-bg-subtle;"));
        tile.setOnMouseExited(e -> tile.setStyle(null));
        return tile;
    }

    /// A {@link TitledPane} with a "Reset to defaults" button at the top
    /// right of its header. {@code TitledPane} only reserves space for its
    /// title text by default, so the header is replaced entirely with a
    /// custom {@code graphic} (title label + spacer + button).
    private TitledPane groupPane(String title, VBox tiles, Runnable onReset) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(Styles.TITLE_4);

        Button resetButton = new Button(Localization.lang("Reset to defaults"));
        resetButton.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        resetButton.setOnAction(e -> onReset.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleLabel, spacer, resetButton);
        header.setAlignment(Pos.CENTER_LEFT);
        // Stretches the header to fill the pane's title row without a
        // reactive prefWidth binding to pane.widthProperty() (tried first,
        // removed while chasing Settings-specific resize lag) - an
        // unbounded max width lets TitledPaneSkin's own layout pass stretch
        // it directly, the same pass that already runs on every resize
        // regardless, instead of an extra property-invalidation listener
        // recomputing on top of that on every pixel.
        header.setMaxWidth(Double.MAX_VALUE);

        TitledPane pane = new TitledPane("", tiles);
        pane.setCollapsible(false);
        pane.setGraphic(header);
        return pane;
    }
}
