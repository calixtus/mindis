package org.mindis.gui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import com.dlsc.fxmlkit.fxml.FxmlKit;
import com.dlsc.gemsfx.PowerPane;

import io.avaje.inject.BeanScope;

import java.util.List;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import org.jspecify.annotations.Nullable;

import org.mindis.core.export.PlanExportService;
import org.mindis.core.l10n.Localization;
import org.mindis.core.logging.LoggingBootstrap;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.preferences.AccentColor;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.gui.di.AvajeDiAdapter;
import org.mindis.gui.logging.AlertOnErrorHandler;
import org.mindis.gui.logging.LogConsoleHandler;
import org.mindis.gui.logging.LogConsoleModel;
import org.mindis.gui.modules.AboutModule;
import org.mindis.gui.modules.DashboardModule;
import org.mindis.gui.modules.RolesModule;
import org.mindis.gui.modules.ServersModule;
import org.mindis.gui.modules.ServicesModule;
import org.mindis.gui.modules.SettingsModule;
import org.mindis.gui.modules.TemplatesModule;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.theme.ThemeStyler;
import org.mindis.gui.shell.AppShell;
import org.mindis.gui.shell.ShellModule;
import org.mindis.gui.shell.ShellOverlays;

/// Application entry point. Owns the Avaje [BeanScope] (single scope per
/// application, PLAN.md section 2.4), applies preferences (locale, theme,
/// window geometry) before the first scene and rebuilds the UI on language
/// change.
// NullAway: the JavaFX launcher instantiates this via a no-arg constructor
// and populates real state via start(Stage), never a constructor we control.
@SuppressWarnings("NullAway.Init")
public class MinDisApp extends Application {

    private static final List<Integer> APP_ICON_SIZES = List.of(16, 32, 48, 64, 128, 256, 512);

    private BeanScope beanScope;
    private PreferencesService preferencesService;
    private UiPreferences uiPreferences;
    private LiveDatabase liveDatabase;
    private DocumentSession documentSession;
    private Stage stage;
    private AppShell shell;
    private PowerPane powerPane;
    private ShellOverlays overlays;
    private final LogConsoleModel logConsole = new LogConsoleModel();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        LoggingBootstrap.configure();
        Logger.getLogger("").addHandler(new AlertOnErrorHandler());
        Logger.getLogger("").addHandler(new LogConsoleHandler(logConsole));

        this.stage = primaryStage;
        beanScope = BeanScope.builder().build();
        preferencesService = beanScope.get(PreferencesService.class);
        uiPreferences = beanScope.get(UiPreferences.class);
        // Built exactly once: the stores (and any unsaved edits in them)
        // survive UI rebuilds - buildShell() only rewires views to them.
        liveDatabase = new LiveDatabase(beanScope.get(AppDatabase.class),
                beanScope.get(RoleRepository.class),
                beanScope.get(ServerRepository.class),
                beanScope.get(TemplateRepository.class),
                beanScope.get(ServiceRepository.class),
                beanScope.get(ArchivedServiceRepository.class));
        documentSession = new DocumentSession(liveDatabase, preferencesService,
                () -> stage.getScene() == null ? null : stage.getScene().getWindow());

        MinDisPreferences preferences = preferencesService.get();
        Localization.setLocale(preferences.locale());
        // After the locale: a new document seeds the default roles with
        // localized names.
        documentSession.openLastDocumentOrNew();

        // Theme, accent and font are all baked into one user-agent stylesheet
        // (base theme @import + .root overrides), reapplied whenever any input
        // changes. Two-arg subscribe does not fire initially; the explicit
        // apply below seeds the first render.
        uiPreferences.themeProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.accentColorProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.fontFamilyProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.fontSizeProperty().subscribe((_, _) -> applyAppearance());
        uiPreferences.toolbarButtonDisplayProperty().subscribe((_, _) -> {
            if (shell != null) {
                applyToolbarButtonDisplay(shell);
            }
        });
        // Follow the OS light/dark scheme live while the theme is SYSTEM.
        Platform.getPreferences().colorSchemeProperty().subscribe((_, _) -> {
            if (uiPreferences.themeProperty().get() == MinDisPreferences.Theme.SYSTEM) {
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
        // The PowerPane is the scene root and holds the overlay layers
        // (dialogs, info center, bottom drawer); the shell is its content.
        // Built before the shell because the modules take ShellOverlays as a
        // constructor dependency.
        powerPane = new PowerPane();
        // Author-origin: the overlay panes each override
        // getUserAgentStylesheet(), so only an author stylesheet outranks the
        // literals they hardcode (see power-pane.css and ThemeStyler).
        powerPane.getStylesheets().add(
                AppShell.class.getResource("power-pane.css").toExternalForm());
        overlays = new ShellOverlays(powerPane);
        shell = buildShell();
        powerPane.setContent(shell);
        Scene scene = new Scene(powerPane, 960, 640);
        addDocumentAccelerators(scene);
        stage.setScene(scene);
        stage.titleProperty().bind(documentSession.titleBinding());
        // Closing with unsaved edits asks first; a cancelled prompt keeps the
        // window open.
        stage.setOnCloseRequest(event -> {
            if (!documentSession.confirmDropUnsavedChanges()) {
                event.consume();
            }
        });
        restoreWindowBounds(preferences.windowBounds());
        stage.show();
        // On Windows, launching from a terminal/IDE can leave the new window
        // behind whatever had focus; force it to the foreground on startup.
        stage.toFront();
        stage.requestFocus();
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
        saveSidebarWidth();
        if (beanScope != null) {
            beanScope.close();
        }
    }

    private AppShell buildShell() {
        Double sidebarWidth = shell == null
                ? preferencesService.get().sidebarWidth()
                : Double.valueOf(shell.getSidebarWidth());
        // Built before the AppShell (not inline in the varargs list below) so
        // buildGlobalToolbar(...) can bind to this exact instance afterward.
        ServicesModule servicesModule = new ServicesModule(Localization.lang("Services"),
                liveDatabase.services(), liveDatabase.roles(), liveDatabase.servers(),
                beanScope.get(TemplateRepository.class),
                beanScope.get(RoleRepository.class),
                new PlanningViewModel(
                        beanScope.get(PlanningService.class),
                        preferencesService,
                        beanScope.get(PlanExportService.class),
                        beanScope.get(ArchivedServiceRepository.class)),
                overlays);
        // The collection switcher (sidebar top) owns the document actions now:
        // switching collection is opening a file, and its dropdown carries New,
        // Open other, Save as and Edit collection. An assignment pick dirties
        // its service record and archiving dirties the archive, so
        // LiveDatabase#dirtyProperty already drives the switcher's save button
        // for the whole document.
        CollectionSwitcher switcher = new CollectionSwitcher(documentSession, liveDatabase,
                servicesModule.solvingProperty());
        AppShell.Builder builder = AppShell.builder(
                                new DashboardModule(Localization.lang("Dashboard")),
                                new RolesModule(Localization.lang("Roles"),
                                        liveDatabase.roles(),
                                        beanScope.get(RoleRepository.class), overlays),
                                new ServersModule(Localization.lang("Servers"),
                                        liveDatabase.servers(), liveDatabase.roles(),
                                        beanScope.get(ServerRepository.class),
                                        beanScope.get(RoleRepository.class), uiPreferences, overlays),
                                new TemplatesModule(Localization.lang("Templates"),
                                        liveDatabase.templates(), liveDatabase.roles(),
                                        beanScope.get(RoleRepository.class), overlays),
                                servicesModule)
                        .sidebarHeader(switcher)
                        .bottomModule(new AboutModule(Localization.lang("About"), getHostServices(), logConsole))
                        .bottomModule(new SettingsModule(Localization.lang("Settings"), uiPreferences));
        if (sidebarWidth != null) {
            builder.initialSidebarWidth(sidebarWidth);
        }
        AppShell built = builder.build();
        switcher.bindCollapsed(built.collapsedProperty());
        applyToolbarButtonDisplay(built);
        return built;
    }

    /// Applies the "text / icon / both" toolbar-button setting by tagging the
    /// shell root with a mode style class the CSS keys off (see
    /// `shell.css`). Reapplied on a rebuild and when the setting
    /// changes.
    private void applyToolbarButtonDisplay(AppShell target) {
        target.getStyleClass().removeAll("toolbar-mode-text", "toolbar-mode-icon", "toolbar-mode-both");
        target.getStyleClass().add(switch (uiPreferences.toolbarButtonDisplayProperty().get()) {
            case TEXT -> "toolbar-mode-text";
            case ICON -> "toolbar-mode-icon";
            case BOTH -> "toolbar-mode-both";
        });
    }

    /// Application-wide keyboard shortcuts for the document actions the
    /// collection switcher exposes. Registered on the scene once; the scene
    /// survives a language-driven UI rebuild (only its root is swapped), so
    /// these do too.
    private void addDocumentAccelerators(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                documentSession::onNew);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                documentSession::onOpen);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                documentSession::onSave);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                documentSession::onSaveAs);
    }

    /// Recreates all UI content with the current locale. Called after a
    /// language change; modules are recreated so every label is rebuilt.
    private void rebuildUi() {
        Localization.setLocale(preferencesService.get().locale());
        FxmlKit.setResourceBundle(Localization.getBundle());
        // Preserve the active module across the rebuild instead of snapping back
        // to Dashboard; by module class, since names change with the locale.
        String activeModuleClass = shell == null ? null : shell.getActiveModuleClassName();
        AppShell oldShell = shell;
        shell = buildShell();
        // The discarded modules registered listeners on the long-lived
        // stores; detach them or every rebuild leaks a full module graph.
        if (oldShell != null) {
            oldShell.getModules().forEach(ShellModule::dispose);
        }
        // Only the PowerPane's content is swapped, not the scene root: the
        // overlay layers (and anything currently showing in them) survive.
        powerPane.setContent(shell);
        shell.openModule(activeModuleClass);
        // Rebound, not re-set: the binding's own text is localized, so it has
        // to be rebuilt under the new locale.
        stage.titleProperty().bind(documentSession.titleBinding());
    }

    /// The effective light/dark mode: the OS color scheme when the theme is
    /// [MinDisPreferences.Theme#SYSTEM], otherwise the explicit choice.
    /// Never returns `SYSTEM`.
    private MinDisPreferences.Theme resolveTheme() {
        MinDisPreferences.Theme theme = uiPreferences.themeProperty().get();
        if (theme == MinDisPreferences.Theme.SYSTEM) {
            return Platform.getPreferences().getColorScheme() == ColorScheme.DARK
                    ? MinDisPreferences.Theme.DARK
                    : MinDisPreferences.Theme.LIGHT;
        }
        return theme;
    }

    /// Applies theme, accent and font as a single user-agent stylesheet: the
    /// base AtlantaFX theme `@import`ed, with the accent/font
    /// `.root` overrides appended. One UA stylesheet (rather than a UA
    /// theme plus a Scene override layer) keeps design tokens consistent in
    /// ComboBox popups and other popup windows, which only see the UA stylesheet
    /// - avoiding stale-token CSS warnings when the theme is switched at runtime.
    private void applyAppearance() {
        // resolveTheme() collapses SYSTEM to a concrete LIGHT/DARK, so only
        // those two reach the base-theme choice here.
        MinDisPreferences.Theme theme = resolveTheme();
        String baseUrl = theme == MinDisPreferences.Theme.DARK
                ? new NordDark().getUserAgentStylesheet()
                : new NordLight().getUserAgentStylesheet();
        setUserAgentStylesheet(ThemeStyler.userAgentStylesheet(
                baseUrl,
                theme,
                resolveAccentHex(),
                uiPreferences.fontFamilyProperty().get(),
                uiPreferences.fontSizeProperty().get()));
    }

    /// The base accent hex to apply: a named color's own hex, or - for [AccentColor#DEFAULT] -
    /// the OS accent color from JavaFX platform
    /// preferences.
    // NullAway: AccentColor.baseHex() is @Nullable only for DEFAULT, excluded
    // by the branch above it - an invariant tied to the enum, not the type.
    @SuppressWarnings("NullAway")
    private String resolveAccentHex() {
        AccentColor accent = uiPreferences.accentColorProperty().get();
        if (accent != AccentColor.DEFAULT) {
            return accent.baseHex();
        }
        return ThemeStyler.toWebHex(Platform.getPreferences().getAccentColor());
    }

    private void restoreWindowBounds(MinDisPreferences.@Nullable WindowBounds bounds) {
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

    private void saveSidebarWidth() {
        if (shell == null || preferencesService == null) {
            return;
        }
        double width = shell.getSidebarWidth();
        preferencesService.update(p -> p.withSidebarWidth(width));
    }
}
