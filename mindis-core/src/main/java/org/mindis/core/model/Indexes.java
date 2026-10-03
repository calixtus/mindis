package org.mindis.core.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Lookup maps over a list of entities, the shape almost every resolver needs
/// (a slot's role id to its role, an assignment's server id to its server).
public final class Indexes {

    private Indexes() {
    }

    /// `items` keyed by `key`, in their original order. A later item with an
    /// already-seen key replaces the earlier one, as a repository upsert would.
    public static <K, T> Map<K, T> byKey(Collection<? extends T> items, Function<? super T, ? extends K> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity(), (first, later) -> later,
                LinkedHashMap::new));
    }
}
