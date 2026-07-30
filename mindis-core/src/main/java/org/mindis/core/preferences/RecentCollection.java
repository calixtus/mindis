package org.mindis.core.preferences;

import org.jspecify.annotations.Nullable;

/// One entry in the collection switcher's recent list (PLAN.md sidebar
/// switcher): a document the user opened or saved, remembered by path together
/// with a snapshot of its identity ([org.mindis.core.model.CollectionMeta])
/// so the dropdown can draw its name and logo without opening every file.
///
/// <p>Held in [MinDisPreferences], capped at
/// [MinDisPreferences#MAX_RECENT_COLLECTIONS]. The `path` is the
/// identity of the entry (dedup key); the name and logo are a cache refreshed on
/// each open or save, and `lastOpenedEpochMillis` orders the list.
public record RecentCollection(
        String path,
        @Nullable String displayName,
        @Nullable String logoPngBase64,
        long lastOpenedEpochMillis) {
}
