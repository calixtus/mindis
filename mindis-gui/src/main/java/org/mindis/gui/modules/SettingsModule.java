package org.mindis.gui.modules;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.mindis.core.l10n.Localization;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.workbench.WorkbenchModule;

/**
 * Settings: language and theme, persisted via {@link PreferencesService}.
 * Language changes trigger a full UI rebuild (labels are created with the
 * active locale); theme changes apply immediately.
 */
public class SettingsModule extends WorkbenchModule {

    /**
     * Display names of supported languages are shown in their own language on
     * purpose (never translated), so they stay readable for everyone.
     */
    private static final java.util.Map<String, String> LANGUAGES = java.util.Map.of(
            "en", "English",
            "de", "Deutsch");

    private final PreferencesService preferencesService;
    private final Runnable onLanguageChanged;

    public SettingsModule(String name, PreferencesService preferencesService, Runnable onLanguageChanged) {
        super(name);
        this.preferencesService = preferencesService;
        this.onLanguageChanged = onLanguageChanged;
    }

    @Override
    public Node activate() {
        MinDisPreferences preferences = preferencesService.get();

        ComboBox<String> languageBox = new ComboBox<>(
                FXCollections.observableArrayList("en", "de"));
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
        languageBox.getSelectionModel().select(preferences.languageTag());
        languageBox.valueProperty().addListener((obs, oldTag, newTag) -> {
            if (newTag != null && !newTag.equals(oldTag)) {
                preferencesService.update(p -> p.withLanguageTag(newTag));
                onLanguageChanged.run();
            }
        });

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
        themeBox.getSelectionModel().select(preferences.theme());
        themeBox.valueProperty().addListener((obs, oldTheme, newTheme) -> {
            if (newTheme != null && newTheme != oldTheme) {
                preferencesService.update(p -> p.withTheme(newTheme));
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label(Localization.lang("Language")), 0, 0);
        grid.add(languageBox, 1, 0);
        grid.add(new Label(Localization.lang("Theme")), 0, 1);
        grid.add(themeBox, 1, 1);

        VBox content = new VBox(grid);
        content.setAlignment(Pos.CENTER);
        return content;
    }
}
