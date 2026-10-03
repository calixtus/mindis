package org.mindis.core.model;


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
        RoleId id,
        String name,
        @Nullable Integer minAge,
        @Nullable Integer maxAge,
        int sortOrder) {

    // Stable ids of the seeded default roles (formerly enum constants).
    public static final RoleId ACOLYTE = new RoleId("ACOLYTE");
    public static final RoleId CROSS_BEARER = new RoleId("CROSS_BEARER");
    public static final RoleId THURIFER = new RoleId("THURIFER");
    public static final RoleId BOAT_BEARER = new RoleId("BOAT_BEARER");
    public static final RoleId MASTER_OF_CEREMONIES = new RoleId("MASTER_OF_CEREMONIES");

    public Role {
        name = name == null ? "" : name.strip();
        // An inverted range is not a harmless typo: no server can ever satisfy
        // it, so every slot for this role silently stays unfillable and the
        // solver reports an infeasible plan with no hint why. Rejected here
        // rather than in each editor, so no caller can construct one - the UI
        // keeps the spinners in order and the CSV importer clamps, both at
        // their own boundary.
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException(
                    "Role '" + name + "' has minAge " + minAge + " above maxAge " + maxAge);
        }
    }

    public String displayName() {
        return name;
    }
}
