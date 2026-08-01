package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;

import org.mindis.core.model.Role;

/// Role storage: the roles of the currently open document, ordered by the
/// user's own sort order (see [InMemoryRepository]).
///
/// <p>The five built-in default roles are seeded into a <em>new</em> document
/// ([AppDatabase#newDocument()]), not whenever the list happens to be
/// empty - an opened document whose roster was deliberately emptied must stay
/// empty. Their ids match the former `Role` enum constants, so data
/// referencing those names still resolves.
@Singleton
public final class RoleRepository extends InMemoryRepository<Role> {

    private static final int SORT_ORDER_STEP = 10;

    public RoleRepository() {
        super(Role::id, Comparator.comparingInt(Role::sortOrder).thenComparing(Role::name));
    }

    /// The next free sort order (current max + a step), for a role not yet in the store.
    public synchronized int nextSortOrder() {
        return findAll().stream()
                .mapToInt(Role::sortOrder)
                .max()
                .orElse(-SORT_ORDER_STEP) + SORT_ORDER_STEP;
    }
}
