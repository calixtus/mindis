package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class UpdatePlatformTest {

    @Test
    void recognizesTheThreePublishedPlatforms() {
        assertEquals(Optional.of(UpdatePlatform.WINDOWS), UpdatePlatform.of("Windows 11"));
        assertEquals(Optional.of(UpdatePlatform.MACOS), UpdatePlatform.of("Mac OS X"));
        assertEquals(Optional.of(UpdatePlatform.LINUX), UpdatePlatform.of("Linux"));
    }

    @Test
    void hasNoPackageForOtherSystems() {
        assertEquals(Optional.empty(), UpdatePlatform.of("FreeBSD"));
    }

    @Test
    void manifestNameMatchesThePublishedAsset() {
        assertEquals("latest-windows.json", UpdatePlatform.WINDOWS.manifestFileName());
    }
}
