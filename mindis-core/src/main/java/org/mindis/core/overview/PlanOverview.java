package org.mindis.core.overview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceType;
import org.mindis.core.planning.MinDisConstraintProvider;

/// What the dashboard shows about the open document, as data: no formatted
/// text, no locale, no layout. Rendering it - dates, separators, "n/m" - is the
/// view's job, so the same numbers could drive a chart or an export without
/// unpicking a string. Assignments live on the service slots, so everything is
/// derived straight from the services - there is no separate plan to read.
///
/// @param upcomingServiceCount every service still ahead, unlike
///        [#upcomingServices()], which is capped at what the "next
///        services" widget can show
public record PlanOverview(int unassignedSlots, int totalSlots,
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

    public PlanOverview {
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
    ///
    /// A roster issue the constraint check reports as well is counted once,
    /// through the constraint - an assignment during an absence is one
    /// problem, not two. When the document was too big to check, the
    /// constraint side is missing, so those issues are counted after all
    /// rather than dropped.
    public int problemCount() {
        int conflicts = problems.stream().mapToInt(ProblemCount::assignments).sum();
        if (!problemsChecked) {
            return conflicts + rosterIssues.size();
        }
        return conflicts + (int) rosterIssues.stream()
                .filter(issue -> issue.kind().coveredByConstraint() == null)
                .count();
    }
    /// The overview of `services` as of `now`, read once so two figures cannot
    /// end up disagreeing over a service that starts while it is computed.
    public static PlanOverview of(List<LiturgicalService> services, List<Server> servers, List<Role> roles,
                                  List<ArchivedService> archived, LocalDateTime now) {
        return new PlanOverviewCalculator(services, servers, roles, archived, now).compute();
    }

    /// One entry of the "next services" widget.
    /// `label` is what the cell shows for the service - its own name where
    /// the planner set one, its localized type otherwise (`EnumDisplay`).
    public record UpcomingService(LocalDateTime dateTime, ServiceType type, String label, String location,
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
        ASSIGNED_WHILE_UNAVAILABLE;

        /// The constraint whose check reports the same fact, or null for an
        /// issue only the dashboard looks for - the two views of one problem
        /// must not add up to two problems. Named rather than flagged, so a
        /// constraint that is renamed away breaks the build here.
        public @Nullable String coveredByConstraint() {
            return switch (this) {
                case ASSIGNED_WHILE_UNAVAILABLE -> MinDisConstraintProvider.UNAVAILABLE;
                case INACTIVE_BUT_ASSIGNED -> MinDisConstraintProvider.INACTIVE;
                case NO_QUALIFICATIONS, NO_UPCOMING_DUTY -> null;
            };
        }
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
}
