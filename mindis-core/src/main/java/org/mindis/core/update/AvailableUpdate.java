package org.mindis.core.update;

import org.jspecify.annotations.Nullable;

/// A release that is newer than what runs here, with the file that would be
/// installed. Produced by [UpdateManifests#available]; the UI turns it into
/// the "version X is available" dialog.
public record AvailableUpdate(AppVersion version, AppVersion runningVersion,
                              @Nullable String releaseNotesUrl, UpdateArtifact installer) {
}
