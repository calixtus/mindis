package org.mindis.core.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A liturgical role an altar server can be qualified for (Acolyte, Cross
/// bearer, ...). Configurable and persisted like [Server]: referenced
/// everywhere by [#id()]. Optional [#minAge]/[#maxAge] express
/// an age requirement (years) for filling the role; either may be `null`.
///
/// <p>The built-in defaults (seeded by `RoleRepository`) keep ids equal to
/// the former `Role` enum constants ([#ACOLYTE] etc.) so pre-existing
/// data referencing those names still resolves without migration.
public record Role(
        String id,
        String name,
        @Nullable Integer minAge,
        @Nullable Integer maxAge,
        int sortOrder) {

    // Stable ids of the seeded default roles (formerly enum constants).
    public static final String ACOLYTE = "ACOLYTE";
    public static final String CROSS_BEARER = "CROSS_BEARER";
    public static final String THURIFER = "THURIFER";
    public static final String BOAT_BEARER = "BOAT_BEARER";
    public static final String MASTER_OF_CEREMONIES = "MASTER_OF_CEREMONIES";

    public Role {
        name = name == null ? "" : name.strip();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public String displayName() {
        return name;
    }
}
