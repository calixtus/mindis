package org.mindis.gui.modules;

import java.util.Map;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.workbench.WorkbenchModule;

/**
 * Settings: language, theme and solver time budget, bound bidirectionally to
 * {@link UiPreferences} (persistence and application of the values happen
 * behind those properties).
 */
public class SettingsModule extends WorkbenchModule {

    /**
     * Display names of supported languages are shown in their own language on
     * purpose (never translated), so they stay readable for everyone.
     */
    private static final Map<String, String> LANGUAGES = Map.of(
            "en", "English",
            "de", "Deutsch");

    private final UiPreferences uiPreferences;

    public SettingsModule(String name, UiPreferences uiPreferences) {
        super(name);
        this.uiPreferences = uiPreferences;
    }

    @Override
    public Node activate() {
        ComboBox<String> languageBox = new ComboBox<>(FXCollections.observableArrayList("en", "de"));
        languageBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String tag) {
                return tag == null ? "" : LANGUAGES.getOrDefault(tag, tag);
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });
        languageBox.valueProperty().bindBidirectional(uiPreferences.languageTagProperty());

        ComboBox<MinDisPreferences.Theme> themeBox = new ComboBox<>(
                FXCollections.observableArrayList(MinDisPreferences.Theme.values()));
        themeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MinDisPreferences.Theme theme) {
                if (theme == null) {
                    return "";
                }
                return switch (theme) {
                    case LIGHT -> Localization.lang("Light");
                    case DARK -> Localization.lang("Dark");
                };
            }

            @Override
            public MinDisPreferences.Theme fromString(String string) {
                return null;
            }
        });
        themeBox.valueProperty().bindBidirectional(uiPreferences.themeProperty());

        Spinner<Integer> solverSecondsSpinner = new Spinner<>(5, 600,
                uiPreferences.solverSecondsLimitProperty().get(), 5);
        solverSecondsSpinner.setEditable(true);
        solverSecondsSpinner.getValueFactory().valueProperty()
                .bindBidirectional(uiPreferences.solverSecondsLimitProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label(Localization.lang("Language")), 0, 0);
        grid.add(languageBox, 1, 0);
        grid.add(new Label(Localization.lang("Theme")), 0, 1);
        grid.add(themeBox, 1, 1);
        grid.add(new Label(Localization.lang("Solver time limit (seconds)")), 0, 2);
        grid.add(solverSecondsSpinner, 1, 2);

        VBox content = new VBox(grid);
        content.setAlignment(Pos.CENTER);
        return content;
    }
}
