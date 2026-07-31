package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;

import org.mindis.core.l10n.Localization;
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

    /// Built-in roles seeded into a new document. Names are localized at seed
    /// time and remain user-editable afterwards.
    public static List<Role> defaults() {
        return List.of(
                new Role(Role.ACOLYTE, Localization.lang("Acolyte"), null, null, 0),
                new Role(Role.CROSS_BEARER, Localization.lang("Cross bearer"), null, null, 1),
                new Role(Role.THURIFER, Localization.lang("Thurifer"), null, null, 2),
                new Role(Role.BOAT_BEARER, Localization.lang("Boat bearer"), null, null, 3),
                new Role(Role.MASTER_OF_CEREMONIES, Localization.lang("Master of ceremonies"), null, null, 4));
    }
}
