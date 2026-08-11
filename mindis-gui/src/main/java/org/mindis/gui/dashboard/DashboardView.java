package org.mindis.gui.dashboard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.gui.util.DateTimes;

/// Dashboard board of widgets - key figures, upcoming services and per-server
/// load - each a draggable, resizable card on an invisible column grid. Builds
/// the board from the persisted layout, fills each widget from a
/// [DashboardViewModel.Snapshot] in the widget's own [WidgetViewMode]
/// (a list or one of the diagram kinds), and offers an "add widget" menu of the
/// types not yet on the board (each type is unique).
///
/// The board fills the pane; the add button floats over it, pinned top-right,
/// overlapping the widgets rather than sitting in its own toolbar strip.
///
/// The snapshot is read once, at construction. That is enough because
/// `DashboardModule` builds a fresh view on every activation, so switching to
/// the dashboard always shows current numbers.
public final class DashboardView extends StackPane {

    private final DashboardViewModel viewModel;
    private final DashboardViewModel.Snapshot snapshot;
    private final WidgetBoard board;
    private final MenuButton addWidgetButton = new MenuButton(Localization.lang("Add widget"));

    public DashboardView(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        this.snapshot = viewModel.loadSnapshot();
        this.board = new WidgetBoard(this::persistLayout);

        getStyleClass().add("dashboard");
        getStylesheets().add(DashboardView.class.getResource("dashboard.css").toExternalForm());

        for (WidgetPlacement placement : viewModel.loadLayout()) {
            board.restoreWidget(newWidget(placement));
        }

        ScrollPane boardScroll = new ScrollPane(board);
        boardScroll.setFitToWidth(true);
        boardScroll.getStyleClass().add("dashboard-scroll");
        boardScroll.setPadding(new Insets(16));

        addWidgetButton.setGraphic(new FontIcon("mdi2p-plus"));
        addWidgetButton.getStyleClass().add("dashboard-add-button");
        addWidgetButton.setOnShowing(_ -> rebuildAddMenu());
        StackPane.setAlignment(addWidgetButton, Pos.TOP_RIGHT);
        StackPane.setMargin(addWidgetButton, new Insets(12, 24, 0, 0));
        rebuildAddMenu();

        getChildren().addAll(boardScroll, addWidgetButton);
    }

    /// Populates the add menu with the widget types not currently on the board;
    /// disables the button when every type is already placed.
    private void rebuildAddMenu() {
        addWidgetButton.getItems().clear();
        for (WidgetType type : WidgetType.values()) {
            if (board.placedTypes().contains(type)) {
                continue;
            }
            MenuItem item = new MenuItem(type.title());
            item.setOnAction(_ -> addWidget(type));
            addWidgetButton.getItems().add(item);
        }
        addWidgetButton.setDisable(addWidgetButton.getItems().isEmpty());
    }

    private void addWidget(WidgetType type) {
        board.placeNewWidget(newWidget(type.defaultPlacement()));
    }

    /// A container for `placement`, filled and wired so that picking
    /// another view mode refills just this widget and persists the choice.
    private WidgetContainer newWidget(WidgetPlacement placement) {
        WidgetContainer widget = new WidgetContainer(placement, changed -> {
            fillContent(changed);
            persistLayout();
        });
        fillContent(widget);
        return widget;
    }

    private void fillContent(WidgetContainer widget) {
        widget.content().getChildren().setAll(buildContent(widget.type(), widget.mode()));
    }

    private void persistLayout() {
        viewModel.saveLayout(board.placements());
        addWidgetButton.setDisable(board.placedTypes().size() == WidgetType.values().length);
    }

    private Node buildContent(WidgetType type, WidgetViewMode mode) {
        return switch (type) {
            case SUMMARY -> summaryContent(mode);
            case NEXT_SERVICES -> upcomingContent(mode);
            case SERVER_LOAD -> serverLoadContent(mode);
            case ROLES -> rolesContent(mode);
            case SERVICE_TYPE_MIX -> serviceTypeMixContent(mode);
            case COVERAGE_TREND -> coverageTrendContent(mode);
            case PEOPLE_AHEAD -> peopleAheadContent(mode);
            case ARCHIVE_HISTORY -> archiveHistoryContent(mode);
            case PROBLEMS -> problemsContent(mode);
        };
    }

    /// Everything wrong the board can see: the assignments violating a
    /// constraint, and what is wrong with the roster itself. Both belong in one
    /// place - to the planner they are one question, "what needs fixing?".
    private Node problemsContent(WidgetViewMode mode) {
        List<DashboardViewModel.ProblemCount> problems = snapshot.problems();
        List<DashboardViewModel.RosterIssue> issues = snapshot.rosterIssues();
        if (mode == WidgetViewMode.BAR) {
            List<Charts.Slice> slices = new ArrayList<>();
            // Constraint names double as localization keys.
            problems.forEach(problem -> slices.add(
                    new Charts.Slice(Localization.lang(problem.constraintName()), problem.assignments())));
            Map<DashboardViewModel.RosterIssueKind, Long> countByKind =
                    new EnumMap<>(DashboardViewModel.RosterIssueKind.class);
            issues.forEach(issue -> countByKind.merge(issue.kind(), 1L, Long::sum));
            countByKind.forEach((kind, count) -> slices.add(new Charts.Slice(describe(kind), count)));
            return Charts.horizontalBar(slices.stream()
                    .sorted(Comparator.comparingDouble(Charts.Slice::value).reversed())
                    .toList(), Localization.lang("Count"));
        }
        List<String> rows = new ArrayList<>();
        problems.forEach(problem -> rows.add(
                Localization.lang(problem.constraintName()) + ": " + problem.assignments()));
        if (!snapshot.problemsChecked()) {
            rows.add(Localization.lang("Too many services to check for conflicts here"));
        }
        issues.forEach(issue -> rows.add(issue.serverName() + ": " + describe(issue.kind())));
        if (rows.isEmpty()) {
            return message(Localization.lang("Nothing to fix"));
        }
        return listView(rows);
    }

    /// A widget body that is a sentence rather than data.
    private static Node message(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dashboard-empty");
        label.setWrapText(true);
        return new StackPane(label);
    }

    private Node archiveHistoryContent(WidgetViewMode mode) {
        List<DashboardViewModel.ArchiveMonth> history = snapshot.archiveHistory();
        List<String> months = history.stream().map(month -> DateTimes.month(month.monthStart())).toList();
        return switch (mode) {
            case BAR -> Charts.bar(history.stream()
                    .map(month -> new Charts.Slice(DateTimes.month(month.monthStart()), month.services()))
                    .toList(), Localization.lang("Services"));
            case LIST -> listView(history.stream()
                    .map(month -> DateTimes.month(month.monthStart()) + ": " + month.services())
                    .toList());
            default -> Charts.line(months,
                    List.of(new Charts.Series(Localization.lang("Services"),
                            history.stream().map(month -> (double) month.services()).toList())),
                    Localization.lang("Services"));
        };
    }

    /// Both role questions at once: how much work is left for a role (open
    /// slots) and whether it can be done at all (qualified servers, against the
    /// peak one service needs).
    private Node rolesContent(WidgetViewMode mode) {
        List<DashboardViewModel.RoleStatus> roles = snapshot.roleStatus();
        return switch (mode) {
            // The pie is about work left, so a role with nothing open has no
            // slice - a zero slice would be an invisible entry in the legend.
            case PIE -> Charts.pie(Charts.topWithOthers(roles.stream()
                    .filter(role -> role.openSlots() > 0)
                    .map(role -> new Charts.Slice(role.roleName(), role.openSlots()))
                    .sorted(Comparator.comparingDouble(Charts.Slice::value).reversed())
                    .toList(), Charts.MAX_PIE_SLICES));
            case LIST -> listView(roles.stream()
                    .map(role -> role.roleName() + ": "
                            + Localization.lang("%0 open, %1 qualified, %2 needed at once",
                                    String.valueOf(role.openSlots()), String.valueOf(role.qualifiedServers()),
                                    String.valueOf(role.peakSlots()))
                            + (role.isShort() ? "  !" : ""))
                    .toList());
            default -> Charts.horizontalBar(roles.stream().map(DashboardViewModel.RoleStatus::roleName).toList(),
                    List.of(new Charts.Series(Localization.lang("Open slots"),
                                    roles.stream().map(role -> (double) role.openSlots()).toList()),
                            new Charts.Series(Localization.lang("Qualified servers"),
                                    roles.stream().map(role -> (double) role.qualifiedServers()).toList())),
                    Localization.lang("Count"));
        };
    }

    /// Who is away and whose birthday it is - the two things about the people
    /// themselves that a planner needs to see coming.
    private Node peopleAheadContent(WidgetViewMode mode) {
        List<DashboardViewModel.Absence> absences = snapshot.absencesAhead();
        List<DashboardViewModel.Birthday> birthdays = snapshot.birthdaysAround();
        if (mode == WidgetViewMode.BAR) {
            // Per server rather than per absence: two short holidays and one
            // long one are the same question - how long is this server gone?
            Map<String, Long> daysByServer = new LinkedHashMap<>();
            for (DashboardViewModel.Absence absence : absences) {
                daysByServer.merge(absence.serverName(),
                        ChronoUnit.DAYS.between(absence.start(), absence.end()) + 1, Long::sum);
            }
            List<Charts.Slice> slices = daysByServer.entrySet().stream()
                    .map(entry -> new Charts.Slice(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingDouble(Charts.Slice::value).reversed())
                    .toList();
            return Charts.horizontalBar(slices, Localization.lang("Days away"));
        }
        // One chronological list of both kinds: what matters is what is coming
        // up next, not whether it is a holiday or a birthday.
        record Entry(LocalDate date, String text) {
        }
        List<Entry> entries = new ArrayList<>();
        absences.forEach(absence -> entries.add(new Entry(absence.start(), absence.serverName() + ": "
                + Localization.lang("from %0 until %1",
                        DateTimes.date(absence.start()), DateTimes.date(absence.end())))));
        birthdays.forEach(birthday -> entries.add(new Entry(birthday.date(), birthday.serverName() + ": "
                + Localization.lang("turns %0 on %1",
                        String.valueOf(birthday.age()), DateTimes.date(birthday.date())))));
        return listView(entries.stream()
                .sorted(Comparator.comparing(Entry::date).thenComparing(Entry::text))
                .map(Entry::text)
                .toList());
    }

    private static String describe(DashboardViewModel.RosterIssueKind kind) {
        return switch (kind) {
            case INACTIVE_BUT_ASSIGNED -> Localization.lang("Inactive but assigned");
            case NO_QUALIFICATIONS -> Localization.lang("No qualifications");
            case NO_UPCOMING_DUTY -> Localization.lang("No upcoming duty");
            case ASSIGNED_WHILE_UNAVAILABLE -> Localization.lang("Assigned while unavailable");
        };
    }

    private Node serviceTypeMixContent(WidgetViewMode mode) {
        List<Charts.Slice> slices = snapshot.serviceTypeMix().stream()
                .map(entry -> new Charts.Slice(EnumDisplay.of(entry.type()), entry.count()))
                .toList();
        return switch (mode) {
            case BAR -> Charts.horizontalBar(slices, Localization.lang("Services"));
            case LIST -> listView(slices.stream()
                    .map(slice -> slice.label() + ": " + (long) slice.value())
                    .toList());
            default -> Charts.pie(slices);
        };
    }

    private Node coverageTrendContent(WidgetViewMode mode) {
        List<DashboardViewModel.WeekCoverage> trend = snapshot.coverageTrend();
        List<String> weeks = trend.stream().map(week -> DateTimes.shortDate(week.weekStart())).toList();
        List<Charts.Series> series = List.of(
                new Charts.Series(Localization.lang("Assigned"),
                        trend.stream().map(week -> (double) week.assignedSlots()).toList()),
                new Charts.Series(Localization.lang("Open"),
                        trend.stream().map(week -> (double) week.openSlots()).toList()));
        return switch (mode) {
            case LINE -> Charts.line(weeks, series, Localization.lang("Slots"));
            case AREA -> Charts.area(weeks, series, Localization.lang("Slots"));
            case LIST -> listView(trend.stream()
                    .map(week -> Localization.lang("Week of %0", DateTimes.date(week.weekStart()))
                            + ": " + week.assignedSlots() + "/" + (week.assignedSlots() + week.openSlots()))
                    .toList());
            default -> Charts.stackedBar(weeks, series, Localization.lang("Slots"));
        };
    }

    private Node summaryContent(WidgetViewMode mode) {
        if (snapshot.isEmpty()) {
            Label label = new Label(Localization.lang("No plan saved yet"));
            label.getStyleClass().add("dashboard-summary");
            label.setWrapText(true);
            return label;
        }
        if (mode == WidgetViewMode.DONUT) {
            return Charts.donut(
                    List.of(new Charts.Slice(Localization.lang("Assigned"), snapshot.assignedSlotsAhead()),
                            new Charts.Slice(Localization.lang("Open"), snapshot.openSlotsAhead())),
                    snapshot.coveragePercent() + "%", Localization.lang("Slots assigned"));
        }
        return new KeyFigures(
                tile(String.valueOf(snapshot.upcomingServiceCount()), Localization.lang("Upcoming services"), ""),
                tile(String.valueOf(snapshot.openSlotsAhead()), Localization.lang("Open slots"),
                        snapshot.openSlotsAhead() == 0 ? "dashboard-tile-good" : "dashboard-tile-warn"),
                tile(snapshot.coveragePercent() + "%", Localization.lang("Slots assigned"), ""),
                tile(problemTileValue(), Localization.lang("Problems"),
                        snapshot.problemCount() == 0 ? "dashboard-tile-good" : "dashboard-tile-warn"),
                tile(String.valueOf(snapshot.activeServers()), Localization.lang("Active servers"), ""),
                tile(String.valueOf(snapshot.roles()), Localization.lang("Roles"), ""));
    }

    /// The conflict count is only as complete as the check that produced it;
    /// when the document was too big to check, the roster issues are all that
    /// is known, and the figure says so rather than claiming to be the total.
    private String problemTileValue() {
        return snapshot.problemsChecked()
                ? String.valueOf(snapshot.problemCount())
                : snapshot.problemCount() + "+";
    }

    /// One key figure: the number big, its meaning small underneath.
    private static Node tile(String value, String caption, String extraStyleClass) {
        Label number = new Label(value);
        number.getStyleClass().add("dashboard-tile-value");
        Label label = new Label(caption);
        label.getStyleClass().add("dashboard-tile-caption");
        label.setWrapText(true);
        VBox tile = new VBox(number, label);
        tile.getStyleClass().add("dashboard-tile");
        // A tile never insists on its own width: in a narrow card the row wraps
        // first, and a caption too long for what is left wraps inside its tile.
        tile.setMinWidth(0);
        if (!extraStyleClass.isBlank()) {
            tile.getStyleClass().add(extraStyleClass);
        }
        return tile;
    }

    private Node upcomingContent(WidgetViewMode mode) {
        List<DashboardViewModel.UpcomingService> upcoming = snapshot.upcomingServices();
        if (mode == WidgetViewMode.STACKED_BAR) {
            List<String> labels = upcoming.stream()
                    .map(service -> DateTimes.shortDate(service.dateTime().toLocalDate()))
                    .toList();
            return Charts.stackedBar(labels,
                    List.of(new Charts.Series(Localization.lang("Assigned"), upcoming.stream()
                                    .map(service -> (double) service.assignedSlots()).toList()),
                            new Charts.Series(Localization.lang("Open"), upcoming.stream()
                                    .map(service -> (double) (service.totalSlots() - service.assignedSlots()))
                                    .toList())),
                    Localization.lang("Slots"));
        }
        ListView<DashboardViewModel.UpcomingService> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(upcoming));
        list.setCellFactory(_ -> new UpcomingServiceCell());
        list.setPlaceholder(new Label(Localization.lang("Nothing to show")));
        return list;
    }

    private Node serverLoadContent(WidgetViewMode mode) {
        List<DashboardViewModel.ServerLoad> load = snapshot.serverLoad();
        return switch (mode) {
            case BAR -> Charts.horizontalBar(slices(load), Localization.lang("Assignments"));
            // A pie of "who did how much" only reads with a handful of slices,
            // and a server with no assignment has no slice at all - so the tail
            // is bucketed rather than drawn.
            case PIE -> Charts.pie(Charts.topWithOthers(
                    slices(load).stream().filter(slice -> slice.value() > 0).toList(), Charts.MAX_PIE_SLICES));
            default -> listView(load.stream()
                    .map(entry -> entry.serverName() + ": " + entry.assignments())
                    .toList());
        };
    }

    private static List<Charts.Slice> slices(List<DashboardViewModel.ServerLoad> load) {
        return load.stream().map(entry -> new Charts.Slice(entry.serverName(), entry.assignments())).toList();
    }

    private static ListView<String> listView(List<String> items) {
        ListView<String> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(items));
        return list;
    }
}
