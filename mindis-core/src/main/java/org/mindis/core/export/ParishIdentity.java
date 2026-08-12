package org.mindis.core.export;

import java.util.Base64;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.CollectionMeta;

/// What an export may show of the collection it came from: the parish name and
/// the parish logo, decoded from the Base64 PNG that [CollectionMeta] keeps
/// inside the document.
///
/// <p>A stock icon ([CollectionMeta#logoIcon]) is deliberately not used here -
/// it is an icon-font glyph standing in for a missing logo in the sidebar, not
/// something a parish would want printed on a handout.
public record ParishIdentity(String name, byte @Nullable [] logoPng) {

    private static final ParishIdentity EMPTY = new ParishIdentity("", null);

    public static ParishIdentity none() {
        return EMPTY;
    }

    /// Reads name and logo off the collection metadata; an undecodable logo is
    /// dropped rather than failing the export.
    public static ParishIdentity of(CollectionMeta meta) {
        String displayName = meta.displayName();
        String name = displayName == null ? "" : displayName.strip();
        String logoBase64 = meta.logoPngBase64();
        byte[] logo = null;
        if (logoBase64 != null && !logoBase64.isBlank()) {
            try {
                logo = Base64.getDecoder().decode(logoBase64);
            } catch (IllegalArgumentException e) {
                logo = null;
            }
        }
        if (name.isEmpty() && logo == null) {
            return EMPTY;
        }
        return new ParishIdentity(name, logo);
    }
}
