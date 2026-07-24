package org.mindis.core.preferences;

import org.jspecify.annotations.Nullable;

/// One entry in the collection switcher's recent list (PLAN.md sidebar
/// switcher): a document the user opened or saved, remembered by path together
/// with a snapshot of its identity ({@link org.mindis.core.model.CollectionMeta})
/// so the dropdown can draw its name and logo without opening every file.
///
/// <p>Held in {@link MinDisPreferences}, capped at
/// {@link MinDisPreferences#MAX_RECENT_COLLECTIONS}. The {@code path} is the
/// identity of the entry (dedup key); the name and logo are a cache refreshed on
/// each open or save, and {@code lastOpenedEpochMillis} orders the list.
public record RecentCollection(
        String path,
        @Nullable String displayName,
        @Nullable String logoPngBase64,
        long lastOpenedEpochMillis) {
}
