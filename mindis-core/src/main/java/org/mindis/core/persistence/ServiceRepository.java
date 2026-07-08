package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.preferences.DataDirectory;

/**
 * Service storage: services.json in the user data directory. Upsert by id.
 */
@Singleton
public class ServiceRepository {

    private final JsonStore<LiturgicalService> store;
    private @Nullable List<LiturgicalService> services;

    public ServiceRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("services.json"));
    }

    protected ServiceRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<LiturgicalService> findAll() {
        return List.copyOf(cached());
    }

    public synchronized Optional<LiturgicalService> findById(String id) {
        return findAll().stream().filter(service -> service.id().equals(id)).findFirst();
    }

    public synchronized void save(LiturgicalService service) {
        List<LiturgicalService> list = cached();
        list.removeIf(existing -> existing.id().equals(service.id()));
        list.add(service);
        sort(list);
        store.save(list);
    }

    public synchronized void saveAll(List<LiturgicalService> newServices) {
        List<LiturgicalService> list = cached();
        for (LiturgicalService service : newServices) {
            list.removeIf(existing -> existing.id().equals(service.id()));
            list.add(service);
        }
        sort(list);
        store.save(list);
    }

    public synchronized void delete(String id) {
        List<LiturgicalService> list = cached();
        list.removeIf(existing -> existing.id().equals(id));
        store.save(list);
    }

    /** The live (mutable) cache, loading and sorting it from disk on first access. */
    private List<LiturgicalService> cached() {
        if (services == null) {
            services = new ArrayList<>(store.load());
            sort(services);
        }
        return services;
    }

    private static void sort(List<LiturgicalService> list) {
        list.sort(Comparator.comparing(LiturgicalService::dateTime));
    }
}
