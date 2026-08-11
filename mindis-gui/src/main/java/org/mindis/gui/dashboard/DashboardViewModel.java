package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.planning.ServicePlans;
import org.mindis.core.planning.ViolationChecker;
import org.mindis.core.preferences.DashboardWidgetLayout;
import org.mindis.core.preferences.PreferencesService;

/// ViewModel for the dashboard: owns every repository call and the
/// upcoming-services/server-load aggregation, and hands the view plain data to
/// render. Assignments live on the service slots, so everything is derived
/// straight from the live services - there is no separate plan to read.
@Prototype
public final class DashboardViewModel {

    /// How many upcoming services the "next services" widget can carry. Enough
    /// to fill the widget when it is dragged tall, and to make a stacked bar
    /// chart of the same data worth looking at.
    private static final int MAX_NEXT_SERVICES = 12;

    /// Weeks the coverage trend spans, counted from the current one - about two
    /// months, which is as far ahead as a parish plan usually reaches.
    private static final int TREND_WEEKS = 8;

    /// How far the "away soon" widget looks ahead - roughly the horizon within
    /// which an absence still changes who can be assigned.
    private static final int ABSENCE_HORIZON_DAYS = 60;

    /// How far the same widget looks *back* for birthdays: about two weeks, so
    /// one that has just passed can still be caught up on.
    private static final int BIRTHDAY_LOOKBACK_DAYS = 14;

    /// Months the archive history spans, ending with the current one.
    private static final int HISTORY_MONTHS = 12;

    /// Above this many slots the dashboard stops checking for conflicts: the
    /// double-booking check compares every assignment with every other, and
    /// the board must not stall while it opens.
    private static final int MAX_CHECKED_SLOTS = 2000;

    private final ServiceRepository serviceRepository;
    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;
    private final ArchivedServiceRepository archivedServiceRepository;
    private final PreferencesService preferencesService;

    public DashboardViewModel(ServiceRepository serviceRepository, ServerRepository serverRepository,
                              RoleRepository roleRepository, ArchivedServiceRepository archivedServiceRepository,
                              PreferencesService preferencesService) {
        this.serviceRepository = serviceRepository;
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
        this.archivedServiceRepository = archivedServiceRepository;
        this.preferencesService = preferencesService;
    }

    /// The persisted widget layout, or - when the user has never arranged the
    /// board (null in preferences) - the default arrangement. An unknown widget
    /// id (e.g. a removed widget type from a newer version) is skipped; an
    /// unknown or no-longer-supported view mode falls back to the widget type's
    /// default, so an older or newer layout still loads.
    public List<WidgetPlacement> loadLayout() {
        @Nullable List<DashboardWidgetLayout> saved = preferencesService.get().dashboardWidgets();
        if (saved == null) {
            return defaultLayout();
        }
        List<WidgetPlacement> layout = new ArrayList<>();
        for (DashboardWidgetLayout entry : saved) {
            WidgetType.fromId(entry.widgetId()).ifPresent(type -> layout.add(
                    new WidgetPlacement(type, entry.col(), entry.row(), entry.colSpan(), entry.rowSpan(),
                            type.resolveMode(modeOf(entry)))));
        }
        return layout;
    }

    private static @Nullable WidgetViewMode modeOf(DashboardWidgetLayout entry) {
        @Nullable String saved = entry.viewMode();
        return saved == null ? null : WidgetViewMode.fromId(saved).orElse(null);
    }

    /// Persists the current board arrangement (positions, spans and view modes).
    public void saveLayout(List<WidgetPlacement> placements) {
        List<DashboardWidgetLayout> saved = new ArrayList<>();
        for (WidgetPlacement placement : placements) {
            saved.add(new DashboardWidgetLayout(placement.type().id(),
                    placement.col(), placement.row(), placement.colSpan(), placement.rowSpan(),
                    placement.mode().id()));
        }
        preferencesService.update(preferences -> preferences.withDashboardWidgets(saved));
    }

    private static List<WidgetPlacement> defaultLayout() {
        List<WidgetPlacement> layout = new ArrayList<>();
        for (WidgetType type : WidgetType.values()) {
            layout.add(type.defaultPlacement());
        }
        return layout;
    }

    /// What the board shows, as data: no formatted text, no locale, no layout.
    /// Rendering it - dates, separators, "n/m" - is the view's job, so the same
    /// numbers could drive a chart or an export without unpicking a string.
    ///
    /// @param upcomingServiceCount every service still ahead, unlike
    ///        [#upcomingServices()], which is capped at what the "next
    ///        services" widget can show
    public record Snapshot(int unassignedSlots, int totalSlots,
                           int openSlotsAhead, int slotsAhead,
                           int upcomingServiceCount, int activeServers, int roles,
                           List<UpcomingService> upcomingServices,
                           List<ServerLoad> serverLoad,
                           List<RoleStatus> roleStatus,
                           List<ServiceTypeCount> serviceTypeMix,
                           List<WeekCoverage> coverageTrend,
                           List<Absence> absencesAhead,
                           List<Birthday> birthdaysAround,
                           List<ArchiveMonth> archiveHistory,
                           List<ProblemCount> problems,
                           List<RosterIssue> rosterIssues,
                           boolean problemsChecked) {

        public Snapshot {
            upcomingServices = List.copyOf(upcomingServices);
            serverLoad = List.copyOf(serverLoad);
            roleStatus = List.copyOf(roleStatus);
            serviceTypeMix = List.copyOf(serviceTypeMix);
            coverageTrend = List.copyOf(coverageTrend);
            absencesAhead = List.copyOf(absencesAhead);
            birthdaysAround = List.copyOf(birthdaysAround);
            archiveHistory = List.copyOf(archiveHistory);
            problems = List.copyOf(problems);
            rosterIssues = List.copyOf(rosterIssues);
        }

        /// Whether the document holds no plan at all yet (no slots anywhere).
        public boolean isEmpty() {
            return totalSlots == 0;
        }

        public int assignedSlots() {
            return totalSlots - unassignedSlots;
        }

        public int assignedSlotsAhead() {
            return slotsAhead - openSlotsAhead;
        }

        /// Share of the slots still ahead that have a server, 0-100. Counted
        /// over the upcoming services only, like every other "open slots"
        /// figure on the board: a slot in a service that has already happened
        /// cannot be filled any more, so counting it would report work that
        /// nobody can do. Nothing planned counts as zero rather than as fully
        /// covered.
        public int coveragePercent() {
            return slotsAhead == 0 ? 0 : Math.round(assignedSlotsAhead() * 100f / slotsAhead);
        }

        /// Everything the problems widget lists: the assignments violating a
        /// constraint plus the roster issues.
        public int problemCount() {
            return problems.stream().mapToInt(ProblemCount::assignments).sum() + rosterIssues.size();
        }
    }

    /// One entry of the "next services" widget.
    public record UpcomingService(LocalDateTime dateTime, ServiceType type, String location,
                                  int assignedSlots, int totalSlots) {
    }

    /// One entry of the "assignments per server" widget, most-loaded first.
    public record ServerLoad(String serverName, long assignments) {
    }

    /// One entry of the "service types" widget: how many of the upcoming
    /// services are of that kind.
    public record ServiceTypeCount(ServiceType type, int count) {
    }

    /// One role on the "roles" widget: how many of its slots are still unfilled
    /// across the upcoming services, how many active servers may fill it, and
    /// the most slots a single upcoming service needs for it.
    ///
    /// The two numbers belong together: open slots say how much work is left,
    /// qualified servers say whether that work can be done at all. Fewer
    /// qualified servers than the peak need means the role cannot be staffed
    /// for that service, however the solver shuffles.
    public record RoleStatus(String roleName, int openSlots, int qualifiedServers, int peakSlots) {

        public boolean isShort() {
            return qualifiedServers < peakSlots;
        }
    }

    /// One entry of the "away and birthdays" widget: an active server
    /// unavailable during (part of) the window the widget looks ahead over.
    public record Absence(String serverName, LocalDate start, LocalDate end) {
    }

    /// A birthday of an active server near today - within the same window as
    /// the absences, plus a short look back so one that has just passed is
    /// still there to congratulate on.
    ///
    /// @param date the birthday's occurrence in that window, not the birth date
    /// @param age the age reached on `date`
    public record Birthday(String serverName, LocalDate date, int age) {
    }

    /// What can be wrong with the roster, as far as the dashboard can see.
    public enum RosterIssueKind {
        /// Inactive, yet still holding an assignment in an upcoming service.
        INACTIVE_BUT_ASSIGNED,
        /// Active, but qualified for nothing, so the solver can never use them.
        NO_QUALIFICATIONS,
        /// Active and qualified, but not assigned to anything ahead.
        NO_UPCOMING_DUTY,
        /// Assigned to a service that falls into one of their absences.
        ASSIGNED_WHILE_UNAVAILABLE
    }

    /// One entry of the "roster health" widget.
    public record RosterIssue(RosterIssueKind kind, String serverName) {
    }

    /// One entry of the "problems" widget: how many assignments violate that
    /// constraint. `constraintName` is the constraint's own name, which
    /// doubles as its localization key.
    public record ProblemCount(String constraintName, int assignments) {
    }

    /// One month of the archive history: how many archived services fall into
    /// it, and how many of their slots had been filled. Months without archived
    /// services are kept, so a break in the record stays visible.
    public record ArchiveMonth(LocalDate monthStart, int services, int assignedSlots) {
    }

    /// One week of the coverage trend: the slots of every service in that week,
    /// split into filled and still open. Weeks with no service are kept, so a
    /// gap in the planning reads as a gap.
    public record WeekCoverage(LocalDate weekStart, int assignedSlots, int openSlots) {
    }

    public Snapshot loadSnapshot() {
        List<LiturgicalService> services = serviceRepository.findAll();
        int totalSlots = services.stream().mapToInt(service -> service.slots().size()).sum();
        int unassigned = (int) services.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        int upcomingCount = (int) services.stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .count();
        int activeServers = (int) serverRepository.findAll().stream().filter(Server::active).count();
        List<LiturgicalService> ahead = services.stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .toList();
        int slotsAhead = ahead.stream().mapToInt(service -> service.slots().size()).sum();
        int openAhead = (int) ahead.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        return new Snapshot(unassigned, totalSlots, openAhead, slotsAhead,
                upcomingCount, activeServers, roleRepository.findAll().size(),
                upcomingServices(services), serverLoad(services),
                roleStatus(ahead), serviceTypeMix(ahead), coverageTrend(ahead),
                absencesAhead(), birthdaysAround(), archiveHistory(),
                problems(services, totalSlots), rosterIssues(ahead), totalSlots <= MAX_CHECKED_SLOTS);
    }

    /// Assignments per violated constraint, worst first - the same checks the
    /// services screen shows per assignment, counted over the whole document.
    /// Built through [ServicePlans], not
    /// [org.mindis.core.planning.PlanningService], so reading the board never
    /// creates a solver.
    ///
    /// The unassigned-slot constraint is left out: the summary and the open
    /// slots widget already say that, and it would otherwise dwarf every real
    /// conflict. Skipped entirely above [#MAX_CHECKED_SLOTS], since the
    /// double-booking check is quadratic in the number of assignments and this
    /// runs on the FX thread while the dashboard is being built.
    private List<ProblemCount> problems(List<LiturgicalService> services, int totalSlots) {
        if (totalSlots > MAX_CHECKED_SLOTS) {
            return List.of();
        }
        ServicePlan plan = ServicePlans.build(services, serverRepository.findAll(), roleRepository.findAll(),
                List.of());
        Map<String, Integer> countByConstraint = new LinkedHashMap<>();
        ViolationChecker.violationsByAssignment(plan).values().stream()
                .flatMap(List::stream)
                .filter(constraint -> !constraint.equals(MinDisConstraintProvider.UNASSIGNED))
                .forEach(constraint -> countByConstraint.merge(constraint, 1, Integer::sum));
        return countByConstraint.entrySet().stream()
                .map(entry -> new ProblemCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ProblemCount::assignments).reversed()
                        .thenComparing(ProblemCount::constraintName))
                .toList();
    }

    /// Archived services per month, oldest month first, over a fixed span
    /// ending with the current month - the record of what has actually been
    /// served, which the live services no longer hold once they are archived.
    private List<ArchiveMonth> archiveHistory() {
        LocalDate firstMonth = LocalDate.now().withDayOfMonth(1).minusMonths(HISTORY_MONTHS - 1L);
        List<ArchivedService> archived = archivedServiceRepository.findAll();
        List<ArchiveMonth> history = new ArrayList<>();
        for (int month = 0; month < HISTORY_MONTHS; month++) {
            LocalDate start = firstMonth.plusMonths(month);
            LocalDate end = start.plusMonths(1);
            int count = 0;
            int assigned = 0;
            for (ArchivedService service : archived) {
                LocalDate date = service.dateTime().toLocalDate();
                if (date.isBefore(start) || !date.isBefore(end)) {
                    continue;
                }
                count++;
                assigned += (int) service.slots().stream()
                        .filter(slot -> slot.serverName() != null)
                        .count();
            }
            history.add(new ArchiveMonth(start, count, assigned));
        }
        return history;
    }

    /// Per role: open slots, qualified active servers and the peak need, over
    /// the services still ahead - an open slot in a service that has already
    /// happened cannot be staffed any more.
    ///
    /// Every configured role is listed, so a role nobody is qualified for is
    /// visible rather than absent; a role id no configured role matches (used
    /// by a slot but since deleted) is appended under its raw id, as the server
    /// load does with server ids.
    private List<RoleStatus> roleStatus(List<LiturgicalService> ahead) {
        List<Server> active = serverRepository.findAll().stream().filter(Server::active).toList();
        Map<String, Integer> openByRole = new LinkedHashMap<>();
        Map<String, Integer> peakByRole = new LinkedHashMap<>();
        for (LiturgicalService service : ahead) {
            Map<String, Integer> perService = new LinkedHashMap<>();
            for (Slot slot : service.slots()) {
                perService.merge(slot.role(), 1, Integer::sum);
                if (slot.serverId() == null) {
                    openByRole.merge(slot.role(), 1, Integer::sum);
                }
            }
            perService.forEach((role, count) -> peakByRole.merge(role, count, Math::max));
        }
        List<RoleStatus> status = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (Role role : roleRepository.findAll()) {
            known.add(role.id());
            status.add(new RoleStatus(role.displayName(),
                    openByRole.getOrDefault(role.id(), 0),
                    (int) active.stream().filter(server -> server.qualifications().contains(role.id())).count(),
                    peakByRole.getOrDefault(role.id(), 0)));
        }
        peakByRole.keySet().stream()
                .filter(roleId -> !known.contains(roleId))
                .forEach(roleId -> status.add(new RoleStatus(roleId, openByRole.getOrDefault(roleId, 0), 0,
                        peakByRole.getOrDefault(roleId, 0))));
        // Roles that cannot be staffed at all first, then the tightest ones,
        // then the ones with the most work left.
        return status.stream()
                .sorted(Comparator.comparing(RoleStatus::isShort).reversed()
                        .thenComparingInt(entry -> entry.qualifiedServers() - entry.peakSlots())
                        .thenComparing(Comparator.comparingInt(RoleStatus::openSlots).reversed())
                        .thenComparing(RoleStatus::roleName))
                .toList();
    }

    /// Active servers unavailable within the next weeks, earliest first. Only
    /// the part of an absence that reaches into the window is interesting, but
    /// the real dates are reported, so a long holiday is not cut off silently.
    private List<Absence> absencesAhead() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(ABSENCE_HORIZON_DAYS);
        List<Absence> absences = new ArrayList<>();
        for (Server server : serverRepository.findAll()) {
            if (!server.active()) {
                continue;
            }
            for (UnavailabilityPeriod period : server.unavailabilities()) {
                if (!period.start().isAfter(horizon) && !period.end().isBefore(today)) {
                    absences.add(new Absence(server.displayName(), period.start(), period.end()));
                }
            }
        }
        return absences.stream()
                .sorted(Comparator.comparing(Absence::start).thenComparing(Absence::serverName))
                .toList();
    }

    /// Birthdays of active servers near today, earliest first: the same window
    /// the absences use, plus [#BIRTHDAY_LOOKBACK_DAYS] behind, so one
    /// that has just gone by is still visible.
    private List<Birthday> birthdaysAround() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(BIRTHDAY_LOOKBACK_DAYS);
        LocalDate until = today.plusDays(ABSENCE_HORIZON_DAYS);
        List<Birthday> birthdays = new ArrayList<>();
        for (Server server : serverRepository.findAll()) {
            LocalDate birthDate = server.birthDate();
            if (!server.active() || birthDate == null) {
                continue;
            }
            // Both this year's and next year's occurrence, since the window
            // can straddle the turn of the year.
            for (int year = from.getYear(); year <= until.getYear(); year++) {
                LocalDate occurrence = occurrenceIn(birthDate, year);
                if (!occurrence.isBefore(from) && !occurrence.isAfter(until)) {
                    birthdays.add(new Birthday(server.displayName(), occurrence,
                            occurrence.getYear() - birthDate.getYear()));
                }
            }
        }
        return birthdays.stream()
                .sorted(Comparator.comparing(Birthday::date).thenComparing(Birthday::serverName))
                .toList();
    }

    /// A birth date's occurrence in `year` - 29 February lands on the
    /// 28th in a common year rather than being skipped.
    private static LocalDate occurrenceIn(LocalDate birthDate, int year) {
        int day = Math.min(birthDate.getDayOfMonth(), birthDate.getMonth().length(Year.isLeap(year)));
        return LocalDate.of(year, birthDate.getMonth(), day);
    }

    /// What the roster itself gets wrong - the checks a planner would otherwise
    /// only discover by reading every service. Deliberately not the solver's
    /// constraint check: this is about the roster, not about one plan's score.
    private List<RosterIssue> rosterIssues(List<LiturgicalService> ahead) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Set<String> assignedAhead = new LinkedHashSet<>();
        List<RosterIssue> issues = new ArrayList<>();
        for (LiturgicalService service : ahead) {
            for (Slot slot : service.slots()) {
                String serverId = slot.serverId();
                if (serverId == null) {
                    continue;
                }
                assignedAhead.add(serverId);
                Server server = serversById.get(serverId);
                if (server != null && !server.isAvailableAt(service.dateTime())) {
                    issues.add(new RosterIssue(RosterIssueKind.ASSIGNED_WHILE_UNAVAILABLE, server.displayName()));
                }
            }
        }
        for (Server server : serversById.values()) {
            if (!server.active()) {
                if (assignedAhead.contains(server.id())) {
                    issues.add(new RosterIssue(RosterIssueKind.INACTIVE_BUT_ASSIGNED, server.displayName()));
                }
                continue;
            }
            if (server.qualifications().isEmpty()) {
                issues.add(new RosterIssue(RosterIssueKind.NO_QUALIFICATIONS, server.displayName()));
            } else if (!assignedAhead.contains(server.id())) {
                issues.add(new RosterIssue(RosterIssueKind.NO_UPCOMING_DUTY, server.displayName()));
            }
        }
        return issues.stream()
                .distinct()
                .sorted(Comparator.comparing(RosterIssue::kind).thenComparing(RosterIssue::serverName))
                .toList();
    }

    private static List<ServiceTypeCount> serviceTypeMix(List<LiturgicalService> ahead) {
        Map<ServiceType, Integer> countByType = new EnumMap<>(ServiceType.class);
        ahead.forEach(service -> countByType.merge(service.type(), 1, Integer::sum));
        return countByType.entrySet().stream()
                .map(entry -> new ServiceTypeCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ServiceTypeCount::count).reversed())
                .toList();
    }

    /// Filled versus open slots per week, from the current week onward. Fixed
    /// length rather than "the weeks that have services", so an empty week
    /// stands out as the hole in the planning that it is.
    private static List<WeekCoverage> coverageTrend(List<LiturgicalService> ahead) {
        LocalDate firstWeek = LocalDate.now().with(DayOfWeek.MONDAY);
        List<WeekCoverage> trend = new ArrayList<>();
        for (int week = 0; week < TREND_WEEKS; week++) {
            LocalDate start = firstWeek.plusWeeks(week);
            LocalDate end = start.plusWeeks(1);
            int assigned = 0;
            int open = 0;
            for (LiturgicalService service : ahead) {
                LocalDate date = service.dateTime().toLocalDate();
                if (date.isBefore(start) || !date.isBefore(end)) {
                    continue;
                }
                for (Slot slot : service.slots()) {
                    if (slot.serverId() == null) {
                        open++;
                    } else {
                        assigned++;
                    }
                }
            }
            trend.add(new WeekCoverage(start, assigned, open));
        }
        return trend;
    }

    private static List<UpcomingService> upcomingServices(List<LiturgicalService> services) {
        return services.stream()
                .filter(service -> service.dateTime().isAfter(LocalDateTime.now()))
                .limit(MAX_NEXT_SERVICES)
                .map(service -> new UpcomingService(
                        service.dateTime(),
                        service.type(),
                        service.location(),
                        (int) service.slots().stream().filter(slot -> slot.serverId() != null).count(),
                        service.slots().size()))
                .toList();
    }

    private List<ServerLoad> serverLoad(List<LiturgicalService> services) {
        Map<String, Server> serversById = new LinkedHashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        Map<String, Long> countByServer = new LinkedHashMap<>();
        // Active servers start at zero: someone who is never assigned is the
        // most interesting entry of this widget, and would otherwise be the one
        // entry missing from it. Inactive servers are not expected to serve, so
        // they appear only if they actually hold an assignment.
        serversById.values().stream()
                .filter(Server::active)
                .forEach(server -> countByServer.put(server.id(), 0L));
        services.stream()
                .flatMap(service -> service.slots().stream())
                .forEach(slot -> {
                    if (slot.serverId() != null) {
                        countByServer.merge(slot.serverId(), 1L, Long::sum);
                    }
                });
        return countByServer.entrySet().stream()
                .map(entry -> {
                    Server server = serversById.get(entry.getKey());
                    // An id with no server left (deleted while still assigned)
                    // falls back to the raw id rather than vanishing.
                    return new ServerLoad(server == null ? entry.getKey() : server.displayName(), entry.getValue());
                })
                // Most-loaded first, then by name so equal loads keep a stable,
                // readable order rather than repository order.
                .sorted(Comparator.comparingLong(ServerLoad::assignments).reversed()
                        .thenComparing(ServerLoad::serverName))
                .toList();
    }
}
