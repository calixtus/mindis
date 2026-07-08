package org.mindis.core.planning;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;

/**
 * One role slot of one service; the solver assigns a {@link Server} to it.
 * Mutable by design - Timefold requires a settable planning variable
 * (PLAN.md section 3).
 */
@PlanningEntity
public class Assignment {

    @PlanningId
    private String id;

    private LiturgicalService service;
    private Role role;

    @PlanningVariable(allowsUnassigned = true)
    private @Nullable Server server;

    @PlanningPin
    private boolean pinned;

    @SuppressWarnings("NullAway.Init") // Timefold requires this constructor; it populates id/service/role by reflection.
    public Assignment() {
    }

    public Assignment(String id, LiturgicalService service, Role role) {
        this.id = id;
        this.service = service;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public LiturgicalService getService() {
        return service;
    }

    public Role getRole() {
        return role;
    }

    public @Nullable Server getServer() {
        return server;
    }

    public void setServer(@Nullable Server server) {
        this.server = server;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public LocalDateTime serviceStart() {
        return service.dateTime();
    }

    public LocalDateTime serviceEnd() {
        return service.dateTime().plusMinutes(service.durationMinutes());
    }

    @Override
    public String toString() {
        return "Assignment[" + service.dateTime() + " " + role.name() + " -> "
                + (server == null ? "unassigned" : server.displayName()) + "]";
    }
}
