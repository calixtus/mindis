package org.mindis.core.model;

/**
 * Liturgical roles an altar server can be qualified for. Display names are
 * localized in the UI via Localization; never show {@code name()} to users.
 */
public enum Role {
    ACOLYTE,
    CROSS_BEARER,
    THURIFER,
    BOAT_BEARER,
    MASTER_OF_CEREMONIES
}
