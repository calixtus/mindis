package org.mindis.core.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.Slot;

/// Builds the transient [ServicePlan] view over a set of services: one
/// [Assignment] per role slot, pre-populated from the slot's own stored
/// assignment (server + pin), with the active servers as the value range.
///
/// Deliberately free of the solver and of the repositories: solving is only one
/// of the things a plan is for. A read-only caller - the dashboard counting
/// conflicts through [ViolationChecker] - needs the same structure without
/// paying for a `SolverManager`, which [PlanningService] creates in its
/// constructor.
public final class ServicePlans {

    private ServicePlans() {
    }

    /// @param servers every server; only the active ones become the plan's
    ///        value range, the rest are still resolved for slots that already
    ///        name them (that is how "inactive but assigned" stays visible)
    /// @param roles every configured role; a slot naming a role that no longer
    ///        exists is skipped, since there is nothing to assign it against
    public static ServicePlan build(List<LiturgicalService> services, List<Server> servers, List<Role> roles,
                                    List<PriorAssignment> priorAssignments) {
        Map<String, Server> serversById = new HashMap<>();
        servers.forEach(server -> serversById.put(server.id(), server));
        Map<String, Role> rolesById = new HashMap<>();
        roles.forEach(role -> rolesById.put(role.id(), role));

        List<Assignment> assignments = new ArrayList<>();
        for (LiturgicalService service : services) {
            for (Slot slot : service.slots()) {
                Role role = rolesById.get(slot.role());
                if (role == null) {
                    continue;
                }
                Assignment assignment = new Assignment(
                        new AssignmentKey(service.id(), slot.id()).toId(), service, role);
                if (slot.serverId() != null) {
                    assignment.setServer(serversById.get(slot.serverId()));
                }
                assignment.setPinned(slot.pinned() && assignment.getServer() != null);
                assignments.add(assignment);
            }
        }
        ServicePlan plan = new ServicePlan(servers.stream().filter(Server::active).toList(), assignments);
        plan.setPriorAssignments(priorAssignments);
        return plan;
    }
}
