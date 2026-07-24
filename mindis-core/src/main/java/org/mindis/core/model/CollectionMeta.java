package org.mindis.core.model;

import org.jspecify.annotations.Nullable;

/// A collection's own identity, stored inside its document so it travels with
/// the file: a display name (the parish name, shown in the collection switcher
/// instead of the bare file name) and an optional logo.
///
/// <p>The logo is a small PNG thumbnail encoded as Base64 - kept in the JSON so
/// a collection is self-contained (no sidecar image file) and the switcher can
/// draw it without opening every document. Both fields are optional: an older
/// or hand-edited document without a {@code meta} block, or with only a name,
/// reads back as {@link #empty()} rather than failing the open.
public record CollectionMeta(@Nullable String displayName, @Nullable String logoPngBase64) {

    private static final CollectionMeta EMPTY = new CollectionMeta(null, null);

    /// No name and no logo - the identity of a fresh untitled collection, and
    /// the fallback for a document that predates collection metadata.
    public static CollectionMeta empty() {
        return EMPTY;
    }

    /// Whether this carries no identity at all (the switcher then falls back to
    /// the file name).
    public boolean isEmpty() {
        return (displayName == null || displayName.isBlank()) && logoPngBase64 == null;
    }

    public CollectionMeta withDisplayName(@Nullable String newDisplayName) {
        return new CollectionMeta(newDisplayName, logoPngBase64);
    }

    public CollectionMeta withLogoPngBase64(@Nullable String newLogoPngBase64) {
        return new CollectionMeta(displayName, newLogoPngBase64);
    }
}
