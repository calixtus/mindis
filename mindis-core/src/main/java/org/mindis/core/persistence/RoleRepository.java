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

/**
 * Role storage: roles.json in the user data directory. Upsert by id. When the
 * store is empty (first run), the five built-in default roles are seeded so the
 * app is usable out of the box and legacy data referencing the former enum ids
 * ({@code ACOLYTE}, ...) still resolves.
 */
@Singleton
public class RoleRepository {

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
        store.save(list);
    }

    public synchronized void delete(String id) {
        List<Role> list = cached();
        list.removeIf(existing -> existing.id().equals(id));
        store.save(list);
    }

    /** The live (mutable) cache, loading and seeding/sorting it on first access. */
    private List<Role> cached() {
        if (roles == null) {
            roles = new ArrayList<>(store.load());
            if (roles.isEmpty()) {
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

    /**
     * Built-in roles seeded on first run. Ids match the former {@code Role}
     * enum constants for backward compatibility; names are localized at seed
     * time and remain user-editable afterwards.
     */
    private static List<Role> defaults() {
        return List.of(
                new Role(Role.ACOLYTE, Localization.lang("Acolyte"), null, null, 0),
                new Role(Role.CROSS_BEARER, Localization.lang("Cross bearer"), null, null, 1),
                new Role(Role.THURIFER, Localization.lang("Thurifer"), null, null, 2),
                new Role(Role.BOAT_BEARER, Localization.lang("Boat bearer"), null, null, 3),
                new Role(Role.MASTER_OF_CEREMONIES, Localization.lang("Master of ceremonies"), null, null, 4));
    }
}
