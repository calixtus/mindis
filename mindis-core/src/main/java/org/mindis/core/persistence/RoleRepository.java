package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.Role;
import org.mindis.core.preferences.DataDirectory;

/// Role storage: roles.json in the user data directory. Upsert by id. When the
/// backing file does not exist yet (first run), the five built-in default roles
/// are seeded so the app is usable out of the box and legacy data referencing
/// the former enum ids ({@code ACOLYTE}, ...) still resolves.
///
/// <p>Mutations ({@link #save} and {@link #delete} stage into
/// the in-memory cache only - the single source of truth every reader (GUI,
/// solver, CSV mappers) shares. Disk I/O happens exclusively through
/// {@link #flush()} and {@link #reload()}, driven by the global Save all/Load
/// actions (see {@link AppDatabase}).
@Singleton
public class RoleRepository {

    private static final int SORT_ORDER_STEP = 10;

    private final JsonStore<Role> store;
    private @Nullable List<Role> roles;

    public RoleRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("roles.json"));
    }

    protected RoleRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<Role> findAll() {
        return List.copyOf(cached());
    }

    public synchronized Optional<Role> findById(String id) {
        return findAll().stream().filter(role -> role.id().equals(id)).findFirst();
    }

    public synchronized void save(Role role) {
        List<Role> list = cached();
        list.removeIf(existing -> existing.id().equals(role.id()));
        list.add(role);
        sort(list);
    }

    public synchronized void delete(String id) {
        cached().removeIf(existing -> existing.id().equals(id));
    }

    /// Writes the staged state to disk - the only disk write path.
    public synchronized void flush() {
        store.save(cached());
    }

    /// Discards staged (unflushed) mutations and reloads from disk.
    public synchronized void reload() {
        roles = null;
        cached();
    }

    /// The next free sort order (current max + a step), for a role not yet in the store.
    public synchronized int nextSortOrder() {
        return cached().stream()
                .mapToInt(Role::sortOrder)
                .max()
                .orElse(-SORT_ORDER_STEP) + SORT_ORDER_STEP;
    }

    /// The live (mutable) cache, loading and seeding/sorting it on first access.
    private List<Role> cached() {
        if (roles == null) {
            roles = new ArrayList<>(store.load());
            // Seed only when the file has never been written - not whenever the
            // list is empty, or a reload() of a deliberately emptied roster
            // would silently resurrect the defaults. The one-time bootstrap
            // write below is a first-run convenience, not a staged mutation.
            if (!store.exists()) {
                roles.addAll(defaults());
                store.save(roles);
            }
            sort(roles);
        }
        return roles;
    }

    private static void sort(List<Role> list) {
        list.sort(Comparator.comparingInt(Role::sortOrder).thenComparing(Role::name));
    }

    /// Built-in roles seeded on first run. Ids match the former {@code Role}
    /// enum constants for backward compatibility; names are localized at seed
    /// time and remain user-editable afterwards.
    private static List<Role> defaults() {
        return List.of(
                new Role(Role.ACOLYTE, Localization.lang("Acolyte"), null, null, 0),
                new Role(Role.CROSS_BEARER, Localization.lang("Cross bearer"), null, null, 1),
                new Role(Role.THURIFER, Localization.lang("Thurifer"), null, null, 2),
                new Role(Role.BOAT_BEARER, Localization.lang("Boat bearer"), null, null, 3),
                new Role(Role.MASTER_OF_CEREMONIES, Localization.lang("Master of ceremonies"), null, null, 4));
    }
}
