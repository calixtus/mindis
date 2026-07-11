package org.mindis.core.planning;

/// The concatenated string id every layer actually stores for one {@link
/// Assignment} (Timefold's {@code @PlanningId}, {@link
/// AcceptedPlan.PlannedAssignment#assignmentId()}'s JSON field, {@code
/// ServicesModule}'s per-service lookups) - one canonical construction/parse
/// pair instead of every call site hand-rolling {@code serviceId + ":" +
/// slotId} (and matching it back by string prefix) independently.
public record AssignmentKey(String serviceId, String slotId) {

    private static final String SEPARATOR = ":";

    public String toId() {
        return serviceId + SEPARATOR + slotId;
    }

    /// Whether {@code assignmentId} (an {@link Assignment#getId()} or {@link
    /// AcceptedPlan.PlannedAssignment#assignmentId()}) belongs to {@code
    /// serviceId} - the prefix match every per-service filter used to
    /// hand-roll as {@code id.startsWith(serviceId + ":")}.
    public static boolean belongsToService(String assignmentId, String serviceId) {
        return assignmentId.startsWith(serviceId + SEPARATOR);
    }
}
