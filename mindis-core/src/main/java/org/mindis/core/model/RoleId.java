package org.mindis.core.model;

import java.util.UUID;

/// The identity of a [Role], as every slot, qualification and template
/// requirement refers to it.
///
/// Its own type rather than a `String` so that a role id cannot be passed
/// where a server id - or any other string - is expected: `Slot` used to take
/// both as adjacent strings. The built-in roles keep their former enum names
/// as values (see [Role#ACOLYTE]).
public record RoleId(String value) implements Comparable<RoleId> {

    public static RoleId newId() {
        return new RoleId(UUID.randomUUID().toString());
    }

    @Override
    public int compareTo(RoleId other) {
        return value.compareTo(other.value);
    }

    /// The bare value, so an id reads the same in a log line or a CSV cell as
    /// it does in the document.
    @Override
    public String toString() {
        return value;
    }
}
