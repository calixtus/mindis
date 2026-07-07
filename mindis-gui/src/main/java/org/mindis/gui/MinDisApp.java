package org.mindis.gui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import com.dlsc.fxmlkit.fxml.FxmlKit;

import io.avaje.inject.BeanScope;

import java.util.List;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.mindis.core.l10n.Localization;
import org.mindis.core.logging.LoggingBootstrap;
import org.mindis.core.preferences.AccentColor;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.di.AvajeDiAdapter;
import org.mindis.gui.logging.AlertOnErrorHandler;
import org.mindis.gui.modules.AboutModule;
import org.mindis.gui.modules.DashboardModule;
import org.mindis.gui.modules.PlanningModule;
import org.mindis.gui.modules.RolesModule;
import org.mindis.gui.modules.ServersModule;
import org.mindis.gui.modules.ServicesModule;
import org.mindis.gui.modules.SettingsModule;
import org.mindis.gui.modules.TemplatesModule;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.theme.ThemeStyler;
import org.mindis.workbench.Workbench;

/**
 * Application entry point. Owns the Avaje {@link BeanScope} (single scope per
 * application, PLAN.md section 2.4), applies preferences (locale, theme,
 * window geometry) before the first scene and rebuilds the UI on language
 * change.
 */
public class MinDisApp extends Application {

    private static final List<Integer> APP_ICON_SIZES = List.of(16, 32, 48, 64, 128, 256, 512);

    private BeanScope beanScope;
    private PreferencesService preferencesService;
    private UiPreferences uiPreferences;
    private Stage stage;
    private Workbench workbench;

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

        // Theme, accent and font are all baked into one user-agent stylesheet
        // (base theme @import + .root overrides), reapplied whenever any input
        // changes. Two-arg subscribe does not fire initially; the explicit
        // apply below seeds the first render.
        uiPreferences.themeProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.followSystemThemeProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.accentColorProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.fontFamilyProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.fontSizeProperty().subscribe((_, _) -> applyAppearance());
        // Follow the OS light/dark scheme live while that option is on.
        Platform.getPreferences().colorSchemeProperty().subscribe((_, _) -> {
            if (uiPreferences.followSystemThemeProperty().get()) {
                applyAppearance();
            }
        });
        // The DEFAULT accent tracks the OS accent color live.
        Platform.getPreferences().accentColorProperty().subscribe((_, _) -> {
            if (uiPreferences.accentColorProperty().get() == AccentColor.DEFAULT) {
                applyAppearance();
            }
        });
        // Language changes need a full UI rebuild; the two-arg subscribe does
        // not fire initially (the scene does not exist yet).
        uiPreferences.languageProperty().subscribe((_, _) -> rebuildUi());

        applyAppearance();

        // Deliberate DIP exception (documented in ADR-001): FxmlKit's Tier-2
        // integration is a global DiAdapter - effectively a service locator
        // for FXML controllers. Confined to this composition root; everything
        // else uses constructor injection.
        FxmlKit.setDiAdapter(new AvajeDiAdapter(beanScope));
        FxmlKit.setResourceBundle(Localization.getBundle());

        stage.getIcons().addAll(loadAppIcons());
        workbench = buildWorkbench();
        stage.setScene(new Scene(workbench, 960, 640));
        stage.setTitle(Localization.lang("MinDis - Minister Dispatcher"));
        restoreWindowBounds(preferences.windowBounds());
        stage.show();
    }

    private List<Image> loadAppIcons() {
        return APP_ICON_SIZES.stream()
                .map(size -> new Image(getClass().getResourceAsStream(
                        "/org/mindis/gui/icons/app-icon/mindis-" + size + ".png")))
                .toList();
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
                                new RolesModule(Localization.lang("Roles")),
                                new ServersModule(Localization.lang("Servers")),
                                new TemplatesModule(Localization.lang("Templates")),
                                new ServicesModule(Localization.lang("Services")),
                                new PlanningModule(Localization.lang("Planning")))
                        .bottomModule(new AboutModule(Localization.lang("About"), getHostServices()))
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
        // Preserve the active module across the rebuild instead of snapping back
        // to Dashboard; by module class, since names change with the locale.
        String activeModuleClass = workbench == null ? null : workbench.getActiveModuleClassName();
        workbench = buildWorkbench();
        stage.getScene().setRoot(workbench);
        workbench.openModule(activeModuleClass);
        stage.setTitle(Localization.lang("MinDis - Minister Dispatcher"));
    }

    /**
     * The effective light/dark mode: the OS color scheme when "follow system"
     * is on, otherwise the user's explicit theme choice.
     */
    private MinDisPreferences.Theme resolveTheme() {
        if (uiPreferences.followSystemThemeProperty().get()) {
            return Platform.getPreferences().getColorScheme() == ColorScheme.DARK
                    ? MinDisPreferences.Theme.DARK
                    : MinDisPreferences.Theme.LIGHT;
        }
        return uiPreferences.themeProperty().get();
    }

    /**
     * Applies theme, accent and font as a single user-agent stylesheet: the
     * base AtlantaFX theme {@code @import}ed, with the accent/font
     * {@code .root} overrides appended. One UA stylesheet (rather than a UA
     * theme plus a Scene override layer) keeps design tokens consistent in
     * ComboBox popups and other popup windows, which only see the UA stylesheet
     * - avoiding stale-token CSS warnings when the theme is switched at runtime.
     */
    private void applyAppearance() {
        MinDisPreferences.Theme theme = resolveTheme();
        String baseUrl = switch (theme) {
            case LIGHT -> new NordLight().getUserAgentStylesheet();
            case DARK -> new NordDark().getUserAgentStylesheet();
        };
        setUserAgentStylesheet(ThemeStyler.userAgentStylesheet(
                baseUrl,
                theme,
                resolveAccentHex(),
                uiPreferences.fontFamilyProperty().get(),
                uiPreferences.fontSizeProperty().get()));
    }

    /**
     * The base accent hex to apply: a named color's own hex, or - for {@link
     * AccentColor#DEFAULT} - the OS accent color from JavaFX platform
     * preferences.
     */
    private String resolveAccentHex() {
        AccentColor accent = uiPreferences.accentColorProperty().get();
        if (accent != AccentColor.DEFAULT) {
            return accent.baseHex();
        }
        return ThemeStyler.toWebHex(Platform.getPreferences().getAccentColor());
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
