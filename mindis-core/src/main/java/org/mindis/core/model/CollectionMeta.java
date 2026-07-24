package org.mindis.core.model;

import org.jspecify.annotations.Nullable;

/// A collection's own identity, stored inside its document so it travels with
/// the file: a display name (the parish name, shown in the collection switcher
/// instead of the bare file name), its logo, and the backdrop drawn behind that
/// logo.
///
/// <p>The logo is either a custom image - a small PNG thumbnail encoded as
/// Base64, kept in the JSON so a collection is self-contained (no sidecar image
/// file) - or, when there is none, a stock icon chosen from the icon font by its
/// literal ({@link #logoIcon}). A custom image wins over a stock icon; with
/// neither, the switcher draws its default icon. {@link #logoBackground} lets a
/// low-contrast logo sit on a light or dark rounded backdrop.
///
/// <p>All fields are optional: an older or hand-edited document without a
/// {@code meta} block, or with only some fields, reads back as {@link #empty()}
/// / defaults rather than failing the open.
public record CollectionMeta(
        @Nullable String displayName,
        @Nullable String logoPngBase64,
        @Nullable String logoIcon,
        LogoBackground logoBackground) {

    /// The backdrop drawn behind the logo, to lift a low-contrast image off the
    /// sidebar.
    public enum LogoBackground {
        NONE,
        LIGHT,
        DARK
    }

    private static final CollectionMeta EMPTY = new CollectionMeta(null, null, null, LogoBackground.NONE);

    public CollectionMeta {
        // Null-tolerant: a document that predates the backdrop lacks the field.
        if (logoBackground == null) {
            logoBackground = LogoBackground.NONE;
        }
    }

    /// No name and no logo - the identity of a fresh untitled collection, and
    /// the fallback for a document that predates collection metadata.
    public static CollectionMeta empty() {
        return EMPTY;
    }

    /// Whether this carries no identity at all (the switcher then falls back to
    /// the file name).
    public boolean isEmpty() {
        return (displayName == null || displayName.isBlank())
                && logoPngBase64 == null && logoIcon == null;
    }

    public CollectionMeta withDisplayName(@Nullable String newDisplayName) {
        return new CollectionMeta(newDisplayName, logoPngBase64, logoIcon, logoBackground);
    }

    public CollectionMeta withLogoPngBase64(@Nullable String newLogoPngBase64) {
        return new CollectionMeta(displayName, newLogoPngBase64, logoIcon, logoBackground);
    }

    public CollectionMeta withLogoIcon(@Nullable String newLogoIcon) {
        return new CollectionMeta(displayName, logoPngBase64, newLogoIcon, logoBackground);
    }

    public CollectionMeta withLogoBackground(LogoBackground newLogoBackground) {
        return new CollectionMeta(displayName, logoPngBase64, logoIcon, newLogoBackground);
    }
}
