package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    void parsesPlainVersion() {
        assertEquals(Optional.of(new AppVersion(1, 2, 3)), AppVersion.parse("1.2.3"));
    }

    @Test
    void parsesTagAndSnapshotForms() {
        // The manifest may carry the release tag, and a local build the
        // -SNAPSHOT suffix; both name the same precedence.
        assertEquals(Optional.of(new AppVersion(0, 1, 0)), AppVersion.parse("v0.1.0"));
        assertEquals(Optional.of(new AppVersion(0, 2, 0)), AppVersion.parse("0.2.0-SNAPSHOT"));
        assertEquals(Optional.of(new AppVersion(1, 0, 0)), AppVersion.parse("1.0"));
    }

    @Test
    void rejectsNonVersions() {
        assertEquals(Optional.empty(), AppVersion.parse("dev"));
        assertEquals(Optional.empty(), AppVersion.parse(""));
        assertEquals(Optional.empty(), AppVersion.parse("1.2.3.4"));
    }

    @Test
    void comparesByPrecedence() {
        assertTrue(new AppVersion(0, 2, 0).isNewerThan(new AppVersion(0, 1, 9)));
        assertTrue(new AppVersion(1, 0, 0).isNewerThan(new AppVersion(0, 99, 99)));
        assertTrue(new AppVersion(0, 1, 1).isNewerThan(new AppVersion(0, 1, 0)));
        assertFalse(new AppVersion(0, 1, 0).isNewerThan(new AppVersion(0, 1, 0)));
        assertFalse(new AppVersion(0, 1, 0).isNewerThan(new AppVersion(0, 1, 1)));
    }

    @Test
    void rendersAsThreeParts() {
        assertEquals("0.1.0", new AppVersion(0, 1, 0).toString());
    }

    @Test
    void readsTheVersionTheBuildGenerated() {
        // The resource is filtered from gradle.properties, so the test asserts
        // its shape, not a literal that would need editing every release.
        String text = AppVersion.currentText();
        assertTrue(AppVersion.parse(text).isPresent() || AppVersion.UNKNOWN_TEXT.equals(text),
                "Unexpected version text: " + text);
    }
}
