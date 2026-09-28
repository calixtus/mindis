package org.mindis.core.update;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;

/// Reading a manifest and deciding whether it describes an update - the whole
/// decision, with no I/O, so it is unit-testable without a server
/// (`UpdateManifestsTest`).
public final class UpdateManifests {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // A manifest written by a newer MinDis may carry fields this
            // version does not know; that must not fail the check.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private UpdateManifests() {
    }

    /// @throws IOException when `json` is not a manifest at all (a
    ///         captive-portal HTML page, a truncated response, ...)
    public static UpdateManifest parse(String json) throws IOException {
        return MAPPER.readValue(json, UpdateManifest.class);
    }

    /// @return the update to offer, or empty when the manifest names no newer
    ///         version, has no version at all, or carries no installer for
    ///         this platform - "nothing to offer" in every case, which is why
    ///         they share one empty result rather than each getting an error.
    public static Optional<AvailableUpdate> available(UpdateManifest manifest, AppVersion runningVersion) {
        Optional<AppVersion> released = AppVersion.parse(manifest.version());
        if (released.isEmpty() || !released.get().isNewerThan(runningVersion)) {
            return Optional.empty();
        }
        return manifest.installer().map(installer -> new AvailableUpdate(
                released.get(), runningVersion, manifest.releaseNotesUrl(), installer));
    }
}
