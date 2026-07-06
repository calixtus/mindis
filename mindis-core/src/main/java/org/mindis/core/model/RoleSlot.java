package org.mindis.core.model;

/**
 * How many servers of a given role a service requires. {@code role} is the
 * {@link Role#id()} (the JSON field name stays {@code role} so pre-existing
 * data written with the former enum names still deserializes).
 */
public record RoleSlot(String role, int count) {

    public RoleSlot {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
