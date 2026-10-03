package org.mindis.core.model;

import java.util.UUID;

/// The identity of a [Server], as a filled [Slot] and an archived slot refer
/// to it.
///
/// Its own type rather than a `String` so that a server id cannot be passed
/// where a role id - or any other string - is expected.
public record ServerId(String value) implements Comparable<ServerId> {

    public static ServerId newId() {
        return new ServerId(UUID.randomUUID().toString());
    }

    @Override
    public int compareTo(ServerId other) {
        return value.compareTo(other.value);
    }

    /// The bare value, so an id reads the same in a log line or a CSV cell as
    /// it does in the document.
    @Override
    public String toString() {
        return value;
    }
}
