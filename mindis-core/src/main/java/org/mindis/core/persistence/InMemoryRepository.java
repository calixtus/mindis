package org.mindis.core.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.NullMarked;

/// Storage for one entity type of the currently open document, keyed by id and
/// kept in a fixed order. Purely in-memory: this cache is the single source of
/// truth every reader (GUI stores, solver, CSV mappers) sees live, and disk I/O
/// happens exclusively in [AppDatabase], which fills each repository when a
/// document is opened and collects it back when one is saved.
///
/// Every entity's repository was the same forty lines - upsert by id into an
/// `ArrayList`, re-sort, copy out - differing only in the id accessor and the
/// sort order, so both are constructor parameters here and a subclass adds only
/// what is genuinely its own (see [RoleRepository#nextSortOrder()]).
///
/// Designed for inheritance rather than merely open to it (Effective Java item
/// 19): subclasses supply the two functions, may add queries of their own, and
/// override nothing. All access is `synchronized` on the repository, and the
/// reentrance that implies is relied on - a subclass query may call
/// [#findAll()].
///
/// @param <T> the entity type; immutable, with a stable string id
@NullMarked
abstract class InMemoryRepository<T> {

    private final List<T> items = new ArrayList<>();
    private final Function<T, String> idOf;
    private final Comparator<? super T> order;

    /// @param idOf  the entity's stable id, the upsert and delete key
    /// @param order the order [#findAll()] returns entities in
    protected InMemoryRepository(Function<T, String> idOf, Comparator<? super T> order) {
        this.idOf = idOf;
        this.order = order;
    }

    /// Every entity, in the repository's own order. A copy: callers iterate it
    /// while other threads (the solver) may be writing.
    public synchronized List<T> findAll() {
        return List.copyOf(items);
    }

    public synchronized Optional<T> findById(String id) {
        return items.stream().filter(item -> idOf.apply(item).equals(id)).findFirst();
    }

    /// Inserts `item`, replacing any entity with the same id.
    public synchronized void save(T item) {
        items.removeIf(existing -> idOf.apply(existing).equals(idOf.apply(item)));
        items.add(item);
        items.sort(order);
    }

    public synchronized void delete(String id) {
        items.removeIf(existing -> idOf.apply(existing).equals(id));
    }

    /// Replaces the whole content, e.g. with a freshly opened document's
    /// entities. Only [AppDatabase] calls this.
    synchronized void replaceAll(List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        items.sort(order);
    }
}
