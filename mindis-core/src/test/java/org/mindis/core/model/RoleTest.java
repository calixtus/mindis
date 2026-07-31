package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// The age-range invariant lives in the record rather than in each editor, so
/// it is worth pinning here: an inverted range makes every slot for the role
/// unfillable, which surfaces only as an infeasible plan with no explanation.
class RoleTest {

    @Test
    void constructor_minAgeAboveMaxAge_throws() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new Role("id", "Acolyte", 14, 10, 0));
        String message = String.valueOf(thrown.getMessage());
        assertTrue(message.contains("Acolyte"),
                "message should name the offending role, was: " + message);
    }

    @Test
    void constructor_equalBounds_isAllowed() {
        Role role = new Role("id", "Acolyte", 12, 12, 0);
        assertAll(
                () -> assertEquals(12, role.minAge()),
                () -> assertEquals(12, role.maxAge()));
    }

    @Test
    void constructor_openBounds_areAllowed() {
        assertAll(
                () -> assertEquals(null, new Role("id", "a", null, 10, 0).minAge()),
                () -> assertEquals(null, new Role("id", "a", 10, null, 0).maxAge()),
                () -> assertEquals(null, new Role("id", "a", null, null, 0).minAge()));
    }

    @Test
    void constructor_paddedName_isStripped() {
        assertEquals("Acolyte", new Role("id", "  Acolyte  ", null, null, 0).name());
    }
}
