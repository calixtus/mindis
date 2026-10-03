package org.mindis.gui.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.Theme;
import com.dlsc.gemsfx.PowerPane;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mindis.core.export.PlanExportService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.planning.ArchiveService;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.preferences.AccentColor;
import org.mindis.core.preferences.DataDirectory;
import org.mindis.core.preferences.MinDisPreferences;
import org.mindis.core.preferences.PreferencesService;
import org.mindis.core.update.UpdateService;
import org.mindis.gui.FxTest;
import org.mindis.gui.TestStores;
import org.mindis.gui.dashboard.DashboardView;
import org.mindis.gui.dashboard.DashboardViewModel;
import org.mindis.gui.data.LiveStore;
import org.mindis.gui.logging.LogConsoleModel;
import org.mindis.gui.modules.AboutModule;
import org.mindis.gui.modules.RolesModule;
import org.mindis.gui.modules.ServersModule;
import org.mindis.gui.modules.ServicesModule;
import org.mindis.gui.modules.SettingsModule;
import org.mindis.gui.modules.TemplatesModule;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.preferences.UiPreferences;
import org.mindis.gui.shell.AppShell;
import org.mindis.gui.shell.CrudModule;
import org.mindis.gui.shell.ShellModule;
import org.mindis.gui.shell.ShellOverlays;
import org.mindis.gui.update.UpdateCheckController;

/// Every visible text on every screen reads at 3:1 or better against the fill behind it,
/// in both themes and with every accent.
///
/// Text colours here come from a cascade that fails quietly: an `inherit` from a parent
/// with nothing to inherit, a fill set on a control that its skin's label never sees, a
/// light theme token reused on a dark surface. Each of those rendered black or
/// near-invisible text that every other test passed, so this measures what was painted
/// instead of what the stylesheets meant. Popups are separate windows and not covered.
class ContrastTest {

    /// WCAG's minimum for large text and UI components; the app's body text sits far above it.
    private static final double MINIMUM_CONTRAST = 3.0;

    @TempDir
    Path tempDir;

    static Stream<Arguments> themesAndAccents() {
        return Stream.of(new NordLight(), new NordDark())
                .flatMap(theme -> Stream.of(AccentColor.values())
                        .filter(accent -> accent.baseHex() != null)
                        .map(accent -> Arguments.of(theme, accent)));
    }

    // NullAway: themesAndAccents leaves out DEFAULT, the one accent without a base hex.
    @SuppressWarnings("NullAway")
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("themesAndAccents")
    void everyTextReadsAgainstItsBackground(Theme theme, AccentColor accent) throws InterruptedException {
        List<String> failures = new ArrayList<>();
        FxTest.runAndWait(() -> {
            Application.setUserAgentStylesheet(ThemeStyler.withModenaTokens(theme).getUserAgentStylesheet());
            ThemeStyler.Appearance appearance = new ThemeStyler.Appearance(
                    theme.isDarkMode() ? MinDisPreferences.Theme.DARK : MinDisPreferences.Theme.LIGHT,
                    accent.baseHex(), "", 14);
            try (Screens screens = new Screens(tempDir)) {
                screens.all().forEach((name, screen) -> failures.addAll(lowContrastTexts(name, screen.get(), appearance)));
            } finally {
                Application.setUserAgentStylesheet(null);
            }
        });
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static List<String> lowContrastTexts(String screenName, Node screen, ThemeStyler.Appearance appearance) {
        Scene scene = new Scene(new StackPane(screen), 1300, 900);
        // What MinDisApp's ThemeManager option does for every scene.
        ThemeStyler.apply(scene, appearance);
        // Twice: the first pass creates the skins, whose own nodes only get styled by the second.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        scene.getRoot().applyCss();

        Set<String> failures = new LinkedHashSet<>();
        for (Text text : visibleTexts(scene.getRoot())) {
            if (text.getText() == null || text.getText().isBlank()
                    || !(text.getFill() instanceof Color fill) || fill.getOpacity() <= 0.2) {
                continue;
            }
            Color background = backgroundBehind(text);
            double ratio = contrast(fill, background);
            if (ratio < MINIMUM_CONTRAST) {
                failures.add("%s: \"%s\" %s on %s is %.2f:1 (%s)".formatted(
                        screenName, text.getText(), fill, background, ratio, styleClassPath(text)));
            }
        }
        return List.copyOf(failures);
    }

    private static List<Text> visibleTexts(Node node) {
        List<Text> texts = new ArrayList<>();
        collectVisibleTexts(node, texts);
        return texts;
    }

    private static void collectVisibleTexts(Node node, List<Text> into) {
        if (!node.isVisible()) {
            return;
        }
        if (node instanceof Text text) {
            into.add(text);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectVisibleTexts(child, into));
        }
    }

    /// The topmost mostly-opaque fill of the nearest ancestor that paints one. Ignores
    /// images, gradients and translucent overlays, which no screen draws text on today.
    private static Color backgroundBehind(Node node) {
        for (Node ancestor = node.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof Region region && region.getBackground() != null) {
                List<BackgroundFill> fills = region.getBackground().getFills();
                for (int i = fills.size() - 1; i >= 0; i--) {
                    if (fills.get(i).getFill() instanceof Color color && color.getOpacity() > 0.6) {
                        return color;
                    }
                }
            }
        }
        return node.getScene().getFill() instanceof Color color ? color : Color.WHITE;
    }

    private static double contrast(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relativeLuminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static String styleClassPath(Node node) {
        List<String> path = new ArrayList<>();
        for (Node current = node; current != null && path.size() < 6; current = current.getParent()) {
            path.add(current.getClass().getSimpleName() + current.getStyleClass());
        }
        return String.join(" < ", path);
    }

    /// Every screen built over one small data set: a server, a role and an upcoming
    /// service with that server assigned, so tables, tiles and charts have something to
    /// draw. The first row of each table is selected, since a selected row is a fill of its own.
    private static final class Screens implements AutoCloseable {

        private final PlanningService planning;
        private final Map<String, Supplier<Node>> screens = new LinkedHashMap<>();

        // NullAway: About and the update check only use HostServices to open a browser,
        // which nothing here does.
        @SuppressWarnings("NullAway")
        Screens(Path tempDir) {
            ServerRepository servers = new ServerRepository();
            ServiceRepository services = new ServiceRepository();
            RoleRepository roles = new RoleRepository();
            ArchivedServiceRepository archived = new ArchivedServiceRepository();
            TemplateRepository templates = new TemplateRepository();
            Server server = Server.named(new ServerId("srv1"), "Anna", "Becker");
            servers.save(server);
            LiturgicalService service = new LiturgicalService("s1", LocalDateTime.now().plusDays(1), 60,
                    "St. Mary", ServiceType.SUNDAY_MASS, "",
                    List.of(new Slot(Slot.newId(), new RoleId("ACOLYTE"), new ServerId("srv1"), false)), "");
            services.save(service);

            PreferencesService preferences = new PreferencesService(tempDir.resolve("preferences.json"));
            ShellOverlays overlays = new ShellOverlays(PowerPane::new);
            LiveStore<Role> roleStore = store(roles.findAll(), Role::id);
            LiveStore<Server> serverStore = store(List.of(server), Server::id);
            ArchiveService archiveService = new ArchiveService(roles, servers, services, archived);
            planning = new PlanningService(servers, services, roles, preferences, archiveService);
            PlanningViewModel planningViewModel = new PlanningViewModel(planning, preferences,
                    PlanExportService.withBuiltInFormats(servers, roles,
                            new AppDatabase(roles, servers, templates, services, archived),
                            new DataDirectory(tempDir)),
                    archiveService);
            UiPreferences uiPreferences = new UiPreferences(preferences);

            screens.put("Dashboard", () -> new DashboardView(
                    new DashboardViewModel(TestStores.services(services), TestStores.servers(servers),
                            TestStores.roles(roles), archived, preferences)));
            List<ShellModule> modules = List.of(
                    new RolesModule("Roles", roleStore, roles, overlays),
                    new ServersModule("Servers", serverStore, roleStore, servers, roles, uiPreferences, overlays),
                    new TemplatesModule("Templates", store(templates.findAll(), ServiceTemplate::id),
                            roleStore, roles, overlays),
                    new ServicesModule("Services", store(List.of(service), LiturgicalService::id),
                            roleStore, serverStore, templates, roles, planningViewModel, overlays),
                    new AboutModule("About", null, new LogConsoleModel()),
                    new SettingsModule("Settings", uiPreferences,
                            new UpdateCheckController(new UpdateService(), overlays, null, () -> true)));
            for (ShellModule module : modules) {
                screens.put(module.getName(), () -> withFirstRowSelected(module));
            }
            screens.put("Sidebar", () -> AppShell.builder(modules.get(0), modules.get(1)).build());
            screens.put("Accent controls", Screens::accentControls);
        }

        Map<String, Supplier<Node>> all() {
            return screens;
        }

        @Override
        public void close() {
            planning.close();
        }

        private static Node withFirstRowSelected(ShellModule module) {
            Node content = module.activate();
            if (module instanceof CrudModule<?>) {
                FxTest.find(content, TableView.class).getSelectionModel().selectFirst();
            }
            return content;
        }

        /// AtlantaFX's own accent-filled controls, which no screen happens to show in every state.
        private static Node accentControls() {
            Button accent = new Button("Accent");
            accent.getStyleClass().add("accent");
            Button defaultButton = new Button("Default");
            defaultButton.setDefaultButton(true);
            ToggleButton selected = new ToggleButton("Selected");
            selected.setSelected(true);
            MenuButton accentMenu = new MenuButton("Accent menu");
            accentMenu.getStyleClass().add("accent");
            Button outlined = new Button("Outlined");
            outlined.getStyleClass().addAll("accent", "button-outlined");
            return new VBox(8, accent, defaultButton, selected, accentMenu, outlined);
        }

        private static <T> LiveStore<T> store(List<T> items, Function<T, @Nullable Object> identity) {
            List<T> staged = new ArrayList<>(items);
            return new LiveStore<T>(() -> new ArrayList<>(staged), staged::add, staged::remove,
                    identity::apply, Objects::equals);
        }
    }
}
