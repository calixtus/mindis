package org.mindis.core.overview;

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

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.Indexes;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.RoleId;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServerId;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.model.UnavailabilityPeriod;
import org.mindis.core.overview.PlanOverview.Absence;
import org.mindis.core.overview.PlanOverview.ArchiveMonth;
import org.mindis.core.overview.PlanOverview.Birthday;
import org.mindis.core.overview.PlanOverview.ProblemCount;
import org.mindis.core.overview.PlanOverview.RoleStatus;
import org.mindis.core.overview.PlanOverview.RosterIssue;
import org.mindis.core.overview.PlanOverview.RosterIssueKind;
import org.mindis.core.overview.PlanOverview.ServerLoad;
import org.mindis.core.overview.PlanOverview.ServiceTypeCount;
import org.mindis.core.overview.PlanOverview.UpcomingService;
import org.mindis.core.overview.PlanOverview.WeekCoverage;
import org.mindis.core.planning.MinDisConstraintProvider;
import org.mindis.core.planning.ServicePlan;
import org.mindis.core.planning.ServicePlans;
import org.mindis.core.planning.ViolationChecker;

/// Computes one [PlanOverview]; holds the inputs for the duration of that one
/// computation, so the per-widget figures read as plain queries over them.
final class PlanOverviewCalculator {

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

    private final List<LiturgicalService> services;
    private final List<Server> servers;
    private final List<Role> roles;
    private final List<ArchivedService> archived;
    private final LocalDateTime now;
    private final LocalDate today;

    PlanOverviewCalculator(List<LiturgicalService> services, List<Server> servers, List<Role> roles,
                           List<ArchivedService> archived, LocalDateTime now) {
        this.services = List.copyOf(services);
        this.servers = List.copyOf(servers);
        this.roles = List.copyOf(roles);
        this.archived = List.copyOf(archived);
        this.now = now;
        this.today = now.toLocalDate();
    }

    PlanOverview compute() {
        int totalSlots = services.stream().mapToInt(service -> service.slots().size()).sum();
        int unassigned = (int) services.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        int activeServers = (int) servers.stream().filter(Server::active).count();
        List<LiturgicalService> ahead = services.stream()
                .filter(service -> service.dateTime().isAfter(now))
                .toList();
        int slotsAhead = ahead.stream().mapToInt(service -> service.slots().size()).sum();
        int openAhead = (int) ahead.stream()
                .flatMap(service -> service.slots().stream())
                .filter(slot -> slot.serverId() == null)
                .count();
        return new PlanOverview(unassigned, totalSlots, openAhead, slotsAhead,
                ahead.size(), activeServers, roles.size(),
                upcomingServices(ahead), serverLoad(services),
                roleStatus(ahead), serviceTypeMix(ahead), coverageTrend(ahead),
                absencesAhead(), birthdaysAround(), archiveHistory(),
                problems(ahead, slotsAhead), rosterIssues(ahead), slotsAhead <= MAX_CHECKED_SLOTS);
    }

    /// Assignments per violated constraint, worst first - the same checks the
    /// services screen shows per assignment, counted over the services still
    /// ahead. Like every other figure on the board it ignores what has already
    /// happened: a conflict in a service that is over cannot be resolved any
    /// more, and would otherwise sit on the board forever. Built through
    /// [ServicePlans], not [org.mindis.core.planning.PlanningService], so
    /// reading the board never creates a solver.
    ///
    /// A constraint is counted once per assignment that violates it, however
    /// many partners it was violated with: the checker records a
    /// double-booking once per conflicting partner, so a server in three
    /// overlapping slots would otherwise read as six problems rather than as
    /// the three assignments they are.
    ///
    /// The unassigned-slot constraint is left out: the summary and the open
    /// slots widget already say that, and it would otherwise dwarf every real
    /// conflict. Skipped entirely above [#MAX_CHECKED_SLOTS], since the
    /// double-booking check is quadratic in the number of assignments and this
    /// typically runs on the UI thread while the dashboard is being built.
    private List<ProblemCount> problems(List<LiturgicalService> ahead, int slotsAhead) {
        if (slotsAhead > MAX_CHECKED_SLOTS) {
            return List.of();
        }
        ServicePlan plan = ServicePlans.build(ahead, servers, roles,
                List.of());
        Map<String, Integer> countByConstraint = new LinkedHashMap<>();
        ViolationChecker.violationsByAssignment(plan).values().stream()
                .flatMap(constraints -> constraints.stream().distinct())
                .filter(constraint -> !MinDisConstraintProvider.UNASSIGNED.equals(constraint))
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
        LocalDate firstMonth = today.withDayOfMonth(1).minusMonths(HISTORY_MONTHS - 1L);
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
        List<Server> active = servers.stream().filter(Server::active).toList();
        Map<RoleId, Integer> openByRole = new LinkedHashMap<>();
        Map<RoleId, Integer> peakByRole = new LinkedHashMap<>();
        for (LiturgicalService service : ahead) {
            Map<RoleId, Integer> perService = new LinkedHashMap<>();
            for (Slot slot : service.slots()) {
                perService.merge(slot.role(), 1, Integer::sum);
                if (slot.serverId() == null) {
                    openByRole.merge(slot.role(), 1, Integer::sum);
                }
            }
            perService.forEach((role, count) -> peakByRole.merge(role, count, Math::max));
        }
        List<RoleStatus> status = new ArrayList<>();
        Set<RoleId> known = new LinkedHashSet<>();
        for (Role role : roles) {
            known.add(role.id());
            status.add(new RoleStatus(role.displayName(),
                    openByRole.getOrDefault(role.id(), 0),
                    (int) active.stream().filter(server -> server.qualifications().contains(role.id())).count(),
                    peakByRole.getOrDefault(role.id(), 0)));
        }
        peakByRole.keySet().stream()
                .filter(roleId -> !known.contains(roleId))
                .forEach(roleId -> status.add(new RoleStatus(roleId.value(), openByRole.getOrDefault(roleId, 0), 0,
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
        LocalDate horizon = today.plusDays(ABSENCE_HORIZON_DAYS);
        List<Absence> absences = new ArrayList<>();
        for (Server server : servers) {
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
        LocalDate from = today.minusDays(BIRTHDAY_LOOKBACK_DAYS);
        LocalDate until = today.plusDays(ABSENCE_HORIZON_DAYS);
        List<Birthday> birthdays = new ArrayList<>();
        for (Server server : servers) {
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
        Map<ServerId, Server> serversById = Indexes.byKey(servers, Server::id);
        Set<ServerId> assignedAhead = new LinkedHashSet<>();
        List<RosterIssue> issues = new ArrayList<>();
        for (LiturgicalService service : ahead) {
            for (Slot slot : service.slots()) {
                ServerId serverId = slot.serverId();
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
    private List<WeekCoverage> coverageTrend(List<LiturgicalService> ahead) {
        LocalDate firstWeek = today.with(DayOfWeek.MONDAY);
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

    private static List<UpcomingService> upcomingServices(List<LiturgicalService> ahead) {
        return ahead.stream()
                .limit(MAX_NEXT_SERVICES)
                .map(service -> new UpcomingService(
                        service.dateTime(),
                        service.type(),
                        EnumDisplay.of(service),
                        service.location(),
                        (int) service.slots().stream().filter(slot -> slot.serverId() != null).count(),
                        service.slots().size()))
                .toList();
    }

    private List<ServerLoad> serverLoad(List<LiturgicalService> services) {
        Map<ServerId, Server> serversById = Indexes.byKey(servers, Server::id);
        Map<ServerId, Long> countByServer = new LinkedHashMap<>();
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
                    return new ServerLoad(server == null ? entry.getKey().value() : server.displayName(), entry.getValue());
                })
                // Most-loaded first, then by name so equal loads keep a stable,
                // readable order rather than repository order.
                .sorted(Comparator.comparingLong(ServerLoad::assignments).reversed()
                        .thenComparing(ServerLoad::serverName))
                .toList();
    }
}
