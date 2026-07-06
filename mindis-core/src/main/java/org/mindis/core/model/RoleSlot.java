package org.mindis.core.model;

/**
 * How many servers of a given role a service requires.
 */
public record RoleSlot(Role role, int count) {

    public RoleSlot {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
