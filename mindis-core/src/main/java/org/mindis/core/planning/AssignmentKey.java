package org.mindis.core.planning;

/// The concatenated string id every layer actually stores for one [Assignment] (Timefold's
/// `@PlanningId`, the GUI's per-service slot
/// lookups) - one canonical construction/parse pair instead of every call site
/// hand-rolling `serviceId + ":" + slotId` (and matching it back by
/// string prefix) independently.
public record AssignmentKey(String serviceId, String slotId) {

    private static final String SEPARATOR = ":";

    public String toId() {
        return serviceId + SEPARATOR + slotId;
    }

    /// Whether `assignmentId` (an [Assignment#getId()]) belongs to
    /// `serviceId` - the prefix match every per-service filter used to
    /// hand-roll as `id.startsWith(serviceId + ":")`.
    public static boolean belongsToService(String assignmentId, String serviceId) {
        return assignmentId.startsWith(serviceId + SEPARATOR);
    }
}
