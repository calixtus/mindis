package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.mindis.core.model.ServiceTemplate;

/// Recurring-service template storage: the templates of the currently open
/// document. Upsert by id. Purely in-memory; disk I/O happens exclusively in
/// {@link AppDatabase}.
@Singleton
public class TemplateRepository {

    private final List<ServiceTemplate> templates = new ArrayList<>();

    public synchronized List<ServiceTemplate> findAll() {
        return List.copyOf(templates);
    }

    public synchronized void save(ServiceTemplate template) {
        templates.removeIf(existing -> existing.id().equals(template.id()));
        templates.add(template);
        sort(templates);
    }

    public synchronized void delete(String id) {
        templates.removeIf(existing -> existing.id().equals(id));
    }

    /// Replaces the whole content with a freshly opened document's templates.
    /// Only {@link AppDatabase} calls this.
    synchronized void replaceAll(List<ServiceTemplate> items) {
        templates.clear();
        templates.addAll(items);
        sort(templates);
    }

    private static void sort(List<ServiceTemplate> list) {
        list.sort(Comparator.comparing(ServiceTemplate::dayOfWeek).thenComparing(ServiceTemplate::time));
    }
}
