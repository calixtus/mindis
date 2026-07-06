package org.mindis.gui.planning;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.mindis.core.model.Server;
import org.mindis.core.planning.Assignment;

/**
 * Observable wrapper around a planning {@link Assignment} (PLAN.md section
 * 2.5: core stays JavaFX-free, the GUI wraps). Property changes write through
 * to the wrapped assignment.
 */
public final class AssignmentRow {

    private final Assignment assignment;
    private final ObjectProperty<Server> server;
    private final BooleanProperty pinned;
    private final StringProperty violations = new SimpleStringProperty("");

    public AssignmentRow(Assignment assignment) {
        this.assignment = assignment;
        this.server = new SimpleObjectProperty<>(assignment.getServer());
        this.pinned = new SimpleBooleanProperty(assignment.isPinned());
        this.pinned.addListener((obs, oldValue, newValue) -> assignment.setPinned(newValue));
    }

    public Assignment assignment() {
        return assignment;
    }

    /**
     * A manual swap by the planner: writes through and pins, so the next
     * solve keeps the decision. Clearing the server unpins, so the solver may
     * fill the slot again.
     */
    public void setServerManually(Server newServer) {
        assignment.setServer(newServer);
        server.set(newServer);
        pinned.set(newServer != null);
    }

    public ObjectProperty<Server> serverProperty() {
        return server;
    }

    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    public StringProperty violationsProperty() {
        return violations;
    }
}
