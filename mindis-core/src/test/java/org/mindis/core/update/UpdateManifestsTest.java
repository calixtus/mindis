package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class UpdateManifestsTest {

    private static final String MANIFEST = """
            {
              "version": "0.2.0",
              "platform": "windows",
              "releaseNotesUrl": "https://github.com/calixtus/mindis/releases/tag/v0.2.0",
              "artifacts": [
                {
                  "fileName": "MinDis-0.2.0.msi",
                  "url": "https://example.invalid/MinDis-0.2.0.msi",
                  "sha256": "ABCD",
                  "size": 1234,
                  "kind": "INSTALLER"
                },
                {
                  "fileName": "MinDis-portable-windows.zip",
                  "url": "https://example.invalid/MinDis-portable-windows.zip",
                  "sha256": "ef01",
                  "size": 99,
                  "kind": "PORTABLE"
                }
              ]
            }
            """;

    @Test
    void offersTheInstallerOfANewerRelease() throws IOException {
        UpdateManifest manifest = UpdateManifests.parse(MANIFEST);

        Optional<AvailableUpdate> update = UpdateManifests.available(manifest, new AppVersion(0, 1, 0));

        assertTrue(update.isPresent());
        assertEquals(new AppVersion(0, 2, 0), update.get().version());
        assertEquals("MinDis-0.2.0.msi", update.get().installer().fileName());
        // Checksums are compared as lower-case hex whatever the manifest wrote.
        assertEquals("abcd", update.get().installer().sha256());
        assertEquals("https://github.com/calixtus/mindis/releases/tag/v0.2.0",
                update.get().releaseNotesUrl());
    }

    @Test
    void offersNothingForTheSameOrAnOlderRelease() throws IOException {
        UpdateManifest manifest = UpdateManifests.parse(MANIFEST);

        assertEquals(Optional.empty(), UpdateManifests.available(manifest, new AppVersion(0, 2, 0)));
        assertEquals(Optional.empty(), UpdateManifests.available(manifest, new AppVersion(1, 0, 0)));
    }

    @Test
    void offersNothingWhenTheReleaseHasNoInstaller() throws IOException {
        UpdateManifest manifest = UpdateManifests.parse("""
                {
                  "version": "0.2.0",
                  "artifacts": [
                    {
                      "fileName": "MinDis-portable-linux.zip",
                      "url": "https://example.invalid/MinDis-portable-linux.zip",
                      "sha256": "ef01",
                      "size": 99,
                      "kind": "PORTABLE"
                    }
                  ]
                }
                """);

        // A portable archive is never unpacked over a running installation.
        assertEquals(Optional.empty(), UpdateManifests.available(manifest, new AppVersion(0, 1, 0)));
    }

    @Test
    void unknownFieldsAndMissingListsAreTolerated() throws IOException {
        UpdateManifest manifest = UpdateManifests.parse("""
                { "version": "0.9.0", "channel": "beta" }
                """);

        assertEquals("0.9.0", manifest.version());
        assertTrue(manifest.artifacts().isEmpty());
        assertEquals(Optional.empty(), UpdateManifests.available(manifest, new AppVersion(0, 1, 0)));
    }

    @Test
    void anUnparsableVersionIsNotAnUpdate() throws IOException {
        UpdateManifest manifest = UpdateManifests.parse("""
                { "version": "nightly", "artifacts": [] }
                """);

        assertEquals(Optional.empty(), UpdateManifests.available(manifest, new AppVersion(0, 1, 0)));
    }

    @Test
    void somethingThatIsNotAManifestFails() {
        // A captive portal answering with HTML must not read as a release.
        assertThrows(IOException.class, () -> UpdateManifests.parse("<html>Sign in</html>"));
    }
}
