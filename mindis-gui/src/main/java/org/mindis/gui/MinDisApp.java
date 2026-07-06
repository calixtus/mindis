package org.mindis.gui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import com.dlsc.fxmlkit.fxml.FxmlKit;

import io.avaje.inject.BeanScope;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.mindis.core.l10n.Localization;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.di.AvajeDiAdapter;
import org.mindis.gui.modules.DashboardModule;
import org.mindis.gui.modules.PlanningModule;
import org.mindis.gui.modules.ServersModule;
import org.mindis.gui.modules.ServicesModule;
import org.mindis.gui.modules.SettingsModule;
import org.mindis.workbench.Workbench;

/**
 * Application entry point. Owns the Avaje {@link BeanScope} (single scope per
 * application, PLAN.md section 2.4), applies preferences (locale, theme,
 * window geometry) before the first scene and rebuilds the UI on language
 * change.
 */
public class MinDisApp extends Application {

    private BeanScope beanScope;
    private PreferencesService preferencesService;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        beanScope = BeanScope.builder().build();
        preferencesService = beanScope.get(PreferencesService.class);

        MinDisPreferences preferences = preferencesService.get();
        Localization.setLocale(preferences.locale());
        applyTheme(preferences.theme());
        preferencesService.addListener(updated -> applyTheme(updated.theme()));

        FxmlKit.setDiAdapter(new AvajeDiAdapter(beanScope));
        FxmlKit.setResourceBundle(Localization.getBundle());

        stage.setScene(new Scene(buildWorkbench(), 960, 640));
        stage.setTitle(Localization.lang("MinDis - Minister Dispatcher"));
        restoreWindowBounds(preferences.windowBounds());
        stage.show();
    }

    @Override
    public void stop() {
        saveWindowBounds();
        if (beanScope != null) {
            beanScope.close();
        }
    }

    private Workbench buildWorkbench() {
        return Workbench.builder(
                        new DashboardModule(Localization.lang("Dashboard")),
                        new ServersModule(Localization.lang("Servers")),
                        new ServicesModule(Localization.lang("Services")),
                        new PlanningModule(Localization.lang("Planning")),
                        new SettingsModule(Localization.lang("Settings"), preferencesService, this::rebuildUi))
                .homeTabTitle(Localization.lang("Home"))
                .build();
    }

    /**
     * Recreates all UI content with the current locale. Called after a
     * language change; modules are recreated so every label is rebuilt.
     */
    private void rebuildUi() {
        Localization.setLocale(preferencesService.get().locale());
        FxmlKit.setResourceBundle(Localization.getBundle());
        stage.getScene().setRoot(buildWorkbench());
        stage.setTitle(Localization.lang("MinDis - Minister Dispatcher"));
    }

    private void applyTheme(MinDisPreferences.Theme theme) {
        setUserAgentStylesheet(switch (theme) {
            case LIGHT -> new NordLight().getUserAgentStylesheet();
            case DARK -> new NordDark().getUserAgentStylesheet();
        });
    }

    private void restoreWindowBounds(MinDisPreferences.WindowBounds bounds) {
        if (bounds == null) {
            return;
        }
        if (bounds.maximized()) {
            stage.setMaximized(true);
            return;
        }
        stage.setX(bounds.x());
        stage.setY(bounds.y());
        stage.setWidth(bounds.width());
        stage.setHeight(bounds.height());
    }

    private void saveWindowBounds() {
        if (stage == null || preferencesService == null) {
            return;
        }
        MinDisPreferences.WindowBounds bounds = new MinDisPreferences.WindowBounds(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), stage.isMaximized());
        preferencesService.update(p -> p.withWindowBounds(bounds));
    }
}
