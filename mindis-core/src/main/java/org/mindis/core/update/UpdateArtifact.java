package org.mindis.core.update;

/// One downloadable file of a release, as the manifest describes it.
///
/// @param fileName the asset's own name, also the name it is downloaded under
/// @param url      absolute download URL, pinned to the release it belongs to
///                 (never a "latest" redirect: the checksum below is for
///                 *this* file)
/// @param sha256   lower-case hex SHA-256 of the file, verified after download
/// @param size     size in bytes, for the progress display; 0 when unknown
/// @param kind     what the file is - only [Kind#INSTALLER] can be installed
public record UpdateArtifact(String fileName, String url, String sha256, long size, Kind kind) {

    /// An installer is handed to the operating system; a portable archive is
    /// listed in the manifest for completeness, but MinDis never unpacks one
    /// over its own running installation (docs/adr/010-auto-update.md).
    public enum Kind {
        INSTALLER,
        PORTABLE
    }

    public UpdateArtifact {
        sha256 = sha256 == null ? "" : sha256.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
