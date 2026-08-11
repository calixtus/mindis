package org.mindis.gui.dashboard;

import io.avaje.inject.Prototype;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /// Months the archive history spans, ending with the current one.
    private static final int HISTORY_MONTHS = 12;

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
                           int upcomingServiceCount, int activeServers, int roles,
                           List<UpcomingService> upcomingServices,
                           List<ServerLoad> serverLoad,
                           List<RoleOpenSlots> openSlotsByRole,
                           List<ServiceTypeCount> serviceTypeMix,
                           List<WeekCoverage> coverageTrend,
                           List<RoleQualification> qualificationCoverage,
                           List<Absence> absencesAhead,
                           List<RosterIssue> rosterIssues,
                           List<ArchiveMonth> archiveHistory) {

        public Snapshot {
            upcomingServices = List.copyOf(upcomingServices);
            serverLoad = List.copyOf(serverLoad);
            openSlotsByRole = List.copyOf(openSlotsByRole);
            serviceTypeMix = List.copyOf(serviceTypeMix);
            coverageTrend = List.copyOf(coverageTrend);
            qualificationCoverage = List.copyOf(qualificationCoverage);
            absencesAhead = List.copyOf(absencesAhead);
            rosterIssues = List.copyOf(rosterIssues);
            archiveHistory = List.copyOf(archiveHistory);
        }

        /// Whether the document holds no plan at all yet (no slots anywhere).
        public boolean isEmpty() {
            return totalSlots == 0;
        }

        public int assignedSlots() {
            return totalSlots - unassignedSlots;
        }

        /// Share of slots that have a server, 0-100. An empty document counts
        /// as zero rather than as fully covered.
        public int coveragePercent() {
            return totalSlots == 0 ? 0 : Math.round(assignedSlots() * 100f / totalSlots);
        }
    }

    /// One entry of the "next services" widget.
    public record UpcomingService(LocalDateTime dateTime, ServiceType type, String location,
                                  int assignedSlots, int totalSlots) {
    }

    /// One entry of the "assignments per server" widget, most-loaded first.
    public record ServerLoad(String serverName, long assignments) {
    }

    /// One entry of the "open slots by role" widget: how many slots for that
    /// role are still unfilled, across the services that are still ahead.
    public record RoleOpenSlots(String roleName, int openSlots) {
    }

    /// One entry of the "service types" widget: how many of the upcoming
    /// services are of that kind.
    public record ServiceTypeCount(ServiceType type, int count) {
    }

    /// One entry of the "qualified servers per role" widget: how many active
    /// servers may fill that role, against the most slots a single upcoming
    /// service needs for it. Fewer qualified servers than that peak means the
    /// role cannot be staffed for that service, however the solver shuffles.
    public record RoleQualification(String roleName, int qualifiedServers, int peakSlots) {

        public boolean isShort() {
            return qualifiedServers < peakSlots;
        }
    }

    /// One entry of the "away soon" widget: an active server unavailable during
    /// (part of) the window the widget looks ahead over.
    public record Absence(String serverName, LocalDate start, LocalDate end) {
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
        return new Snapshot(unassigned, totalSlots, upcomingCount, activeServers, roleRepository.findAll().size(),
                upcomingServices(services), serverLoad(services),
                openSlotsByRole(ahead), serviceTypeMix(ahead), coverageTrend(ahead),
                qualificationCoverage(ahead), absencesAhead(), rosterIssues(ahead), archiveHistory());
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

    /// Per configured role: how many active servers may fill it, against the
    /// most slots one upcoming service needs for it. Every role is listed, so a
    /// role nobody is qualified for is visible rather than absent.
    private List<RoleQualification> qualificationCoverage(List<LiturgicalService> ahead) {
        List<Server> active = serverRepository.findAll().stream().filter(Server::active).toList();
        List<RoleQualification> coverage = new ArrayList<>();
        for (Role role : roleRepository.findAll()) {
            int qualified = (int) active.stream()
                    .filter(server -> server.qualifications().contains(role.id()))
                    .count();
            int peak = ahead.stream()
                    .mapToInt(service -> (int) service.slots().stream()
                            .filter(slot -> slot.role().equals(role.id()))
                            .count())
                    .max()
                    .orElse(0);
            coverage.add(new RoleQualification(role.displayName(), qualified, peak));
        }
        // Roles that cannot be staffed first, then the tightest ones.
        return coverage.stream()
                .sorted(Comparator.comparing(RoleQualification::isShort).reversed()
                        .thenComparingInt(entry -> entry.qualifiedServers() - entry.peakSlots())
                        .thenComparing(RoleQualification::roleName))
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

    /// Open slots per role, most-open first. Only services still ahead count:
    /// a slot left open in a service that has already happened cannot be
    /// staffed any more, so it is history, not a task.
    private List<RoleOpenSlots> openSlotsByRole(List<LiturgicalService> ahead) {
        Map<String, String> roleNames = new LinkedHashMap<>();
        roleRepository.findAll().forEach(role -> roleNames.put(role.id(), role.displayName()));
        Map<String, Integer> openByRole = new LinkedHashMap<>();
        ahead.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .forEach(slot -> openByRole.merge(slot.role(), 1, Integer::sum));
        return openByRole.entrySet().stream()
                // A role id with no role left (deleted while still used) falls
                // back to the raw id, as the server load does with server ids.
                .map(entry -> new RoleOpenSlots(roleNames.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparingInt(RoleOpenSlots::openSlots).reversed()
                        .thenComparing(RoleOpenSlots::roleName))
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
