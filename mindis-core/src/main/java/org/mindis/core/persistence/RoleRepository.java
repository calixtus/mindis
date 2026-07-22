package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;

/// Role storage: the roles of the currently open document. Upsert by id.
/// Purely in-memory; disk I/O happens exclusively in {@link AppDatabase}.
///
/// <p>The five built-in default roles are seeded into a <em>new</em> document
/// ({@link AppDatabase#newDocument()}), not whenever the list happens to be
/// empty - an opened document whose roster was deliberately emptied must stay
/// empty. Their ids match the former {@code Role} enum constants, so data
/// referencing those names still resolves.
@Singleton
public class RoleRepository {

    private static final int SORT_ORDER_STEP = 10;

    private final List<Role> roles = new ArrayList<>();

    public synchronized List<Role> findAll() {
        return List.copyOf(roles);
    }

    public synchronized Optional<Role> findById(String id) {
        return roles.stream().filter(role -> role.id().equals(id)).findFirst();
    }

    public synchronized void save(Role role) {
        roles.removeIf(existing -> existing.id().equals(role.id()));
        roles.add(role);
        sort(roles);
    }

    public synchronized void delete(String id) {
        roles.removeIf(existing -> existing.id().equals(id));
    }

    /// Replaces the whole content with a freshly opened document's roles.
    /// Only {@link AppDatabase} calls this.
    synchronized void replaceAll(List<Role> items) {
        roles.clear();
        roles.addAll(items);
        sort(roles);
    }

    /// The next free sort order (current max + a step), for a role not yet in the store.
    public synchronized int nextSortOrder() {
        return roles.stream()
                .mapToInt(Role::sortOrder)
                .max()
                .orElse(-SORT_ORDER_STEP) + SORT_ORDER_STEP;
    }

    private static void sort(List<Role> list) {
        list.sort(Comparator.comparingInt(Role::sortOrder).thenComparing(Role::name));
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
