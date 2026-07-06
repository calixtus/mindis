package org.mindis.gui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import com.dlsc.fxmlkit.fxml.FxmlKit;

import io.avaje.inject.BeanScope;

import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.mindis.core.l10n.Localization;
import org.mindis.core.logging.LoggingBootstrap;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.di.AvajeDiAdapter;
import org.mindis.gui.logging.AlertOnErrorHandler;
import org.mindis.gui.modules.DashboardModule;
import org.mindis.gui.modules.PlanningModule;
import org.mindis.gui.modules.ServersModule;
import org.mindis.gui.modules.ServicesModule;
import org.mindis.gui.modules.SettingsModule;
import org.mindis.gui.preferences.UiPreferences;
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
    private UiPreferences uiPreferences;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        LoggingBootstrap.configure();
        Logger.getLogger("").addHandler(new AlertOnErrorHandler());

        this.stage = primaryStage;
        beanScope = BeanScope.builder().build();
        preferencesService = beanScope.get(PreferencesService.class);
        uiPreferences = beanScope.get(UiPreferences.class);

        MinDisPreferences preferences = preferencesService.get();
        Localization.setLocale(preferences.locale());

        // subscribe(Consumer) fires immediately: applies the persisted theme now
        // and every later change automatically.
        uiPreferences.themeProperty().subscribe(this::applyTheme);
        // Language changes need a full UI rebuild; the two-arg subscribe does
        // not fire initially (the scene does not exist yet).
        uiPreferences.languageProperty().subscribe((_, _) -> rebuildUi());

        // Deliberate DIP exception (documented in ADR-001): FxmlKit's Tier-2
        // integration is a global DiAdapter - effectively a service locator
        // for FXML controllers. Confined to this composition root; everything
        // else uses constructor injection.
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
                        new PlanningModule(Localization.lang("Planning")))
                .bottomModule(new SettingsModule(Localization.lang("Settings"), uiPreferences))
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
