package org.mindis.core.model;

import java.util.UUID;

/**
 * A liturgical role an altar server can be qualified for (Acolyte, Cross
 * bearer, ...). Configurable and persisted like {@link Server}: referenced
 * everywhere by {@link #id()}. Optional {@link #minAge}/{@link #maxAge} express
 * an age requirement (years) for filling the role; either may be {@code null}.
 *
 * <p>The built-in defaults (seeded by {@code RoleRepository}) keep ids equal to
 * the former {@code Role} enum constants ({@link #ACOLYTE} etc.) so pre-existing
 * data referencing those names still resolves without migration.
 */
public record Role(
        String id,
        String name,
        Integer minAge,
        Integer maxAge,
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
