package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mindis.core.model.LiturgicalService;

/// Service storage: the liturgical services of the currently open document,
/// assignments included (an assignment lives on its slot). Upsert by id.
/// Purely in-memory; disk I/O happens exclusively in {@link AppDatabase}.
@Singleton
public class ServiceRepository {

    private final List<LiturgicalService> services = new ArrayList<>();

    public synchronized List<LiturgicalService> findAll() {
        return List.copyOf(services);
    }

    public synchronized Optional<LiturgicalService> findById(String id) {
        return services.stream().filter(service -> service.id().equals(id)).findFirst();
    }

    public synchronized void save(LiturgicalService service) {
        services.removeIf(existing -> existing.id().equals(service.id()));
        services.add(service);
        sort(services);
    }

    public synchronized void delete(String id) {
        services.removeIf(existing -> existing.id().equals(id));
    }

    /// Replaces the whole content with a freshly opened document's services.
    /// Only {@link AppDatabase} calls this.
    synchronized void replaceAll(List<LiturgicalService> items) {
        services.clear();
        services.addAll(items);
        sort(services);
    }

    private static void sort(List<LiturgicalService> list) {
        list.sort(Comparator.comparing(LiturgicalService::dateTime));
    }
}
