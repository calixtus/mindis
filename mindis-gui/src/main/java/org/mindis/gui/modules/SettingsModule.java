package org.mindis.gui.modules;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.preferences.AppLanguage;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.gui.preferences.PreferenceControls;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.workbench.WorkbenchModule;

/**
 * Settings: language, theme, solver time budget and constraint weights, all
 * bound bidirectionally to {@link UiPreferences} (persistence and application
 * of the values happen behind those properties).
 */
public class SettingsModule extends WorkbenchModule {

    private final UiPreferences uiPreferences;

    public SettingsModule(String name, UiPreferences uiPreferences) {
        super(name, "mdi2c-cog");
        this.uiPreferences = uiPreferences;
    }

    @Override
    public Node activate() {
        Spinner<Integer> solverSecondsSpinner = new Spinner<>(5, 600,
                uiPreferences.solverSecondsLimitProperty().get(), 5);
        solverSecondsSpinner.setEditable(true);
        solverSecondsSpinner.getValueFactory().valueProperty()
                .bindBidirectional(uiPreferences.solverSecondsLimitProperty());

        CheckBox largeSidebarIconsCheck = new CheckBox();
        largeSidebarIconsCheck.selectedProperty().bindBidirectional(uiPreferences.largeSidebarIconsProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        grid.add(new Label(Localization.lang("Language")), 0, 0);
        grid.add(PreferenceControls.choiceBox(AppLanguage.values(), uiPreferences.languageProperty()), 1, 0);
        grid.add(new Label(Localization.lang("Theme")), 0, 1);
        grid.add(PreferenceControls.choiceBox(MinDisPreferences.Theme.values(), uiPreferences.themeProperty()), 1, 1);
        grid.add(new Label(Localization.lang("Solver time limit (seconds)")), 0, 2);
        grid.add(solverSecondsSpinner, 1, 2);
        grid.add(new Label(Localization.lang("Large sidebar icons")), 0, 3);
        grid.add(largeSidebarIconsCheck, 1, 3);

        GridPane weightsGrid = new GridPane();
        weightsGrid.setHgap(12);
        weightsGrid.setVgap(8);
        int row = 0;
        for (String constraintName : MinDisConstraintProvider.tunableSoftConstraints()) {
            Spinner<Integer> weightSpinner = new Spinner<>(0, 20,
                    uiPreferences.softWeightProperty(constraintName).get());
            weightSpinner.getValueFactory().valueProperty()
                    .bindBidirectional(uiPreferences.softWeightProperty(constraintName));
            weightsGrid.add(new Label(Localization.lang(constraintName)), 0, row);
            weightsGrid.add(weightSpinner, 1, row);
            row++;
        }
        TitledPane weightsPane = new TitledPane(Localization.lang("Constraint weights"), weightsGrid);
        weightsPane.setCollapsible(false);

        VBox content = new VBox(24, grid, weightsPane);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(420);
        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }
}
