package org.mindis.core.update;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// What one platform's release manifest says: the released version and the
/// files it consists of. Written by the build (`:gui:updateManifest`) and
/// published as a release asset, so nothing but a static file is needed on the
/// server side.
///
/// @param version          the released version, `major.minor.patch`
/// @param releaseNotesUrl  page to open for "what's new"; `null` when the
///                         release has none
/// @param artifacts        the files of this release for this platform
public record UpdateManifest(String version, @Nullable String releaseNotesUrl, List<UpdateArtifact> artifacts) {

    public UpdateManifest {
        // Null-tolerant: a hand-edited or future manifest may omit the list.
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    /// The installer to offer, if this release has one.
    public Optional<UpdateArtifact> installer() {
        return artifacts.stream()
                .filter(artifact -> artifact.kind() == UpdateArtifact.Kind.INSTALLER)
                .findFirst();
    }
}
