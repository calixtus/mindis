package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.model.LiturgicalService;
import org.mindis.core.preferences.AppDirectories;

/**
 * Service storage: services.json in the user data directory. Upsert by id.
 */
@Singleton
public class ServiceRepository {

    private final JsonStore<LiturgicalService> store;
    private List<LiturgicalService> services;

    public ServiceRepository() {
        this(AppDirectories.userDataDir().resolve("services.json"));
    }

    protected ServiceRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<LiturgicalService> findAll() {
        if (services == null) {
            services = new ArrayList<>(store.load());
            sort();
        }
        return List.copyOf(services);
    }

    public synchronized Optional<LiturgicalService> findById(String id) {
        return findAll().stream().filter(service -> service.id().equals(id)).findFirst();
    }

    public synchronized void save(LiturgicalService service) {
        findAll();
        services.removeIf(existing -> existing.id().equals(service.id()));
        services.add(service);
        sort();
        store.save(services);
    }

    public synchronized void saveAll(List<LiturgicalService> newServices) {
        findAll();
        for (LiturgicalService service : newServices) {
            services.removeIf(existing -> existing.id().equals(service.id()));
            services.add(service);
        }
        sort();
        store.save(services);
    }

    public synchronized void delete(String id) {
        findAll();
        services.removeIf(existing -> existing.id().equals(id));
        store.save(services);
    }

    private void sort() {
        services.sort(Comparator.comparing(LiturgicalService::dateTime));
    }
}
