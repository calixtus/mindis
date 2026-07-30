package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.mindis.core.model.ServiceTemplate;

/// Recurring-service template storage: the templates of the currently open
/// document. Upsert by id. Purely in-memory; disk I/O happens exclusively in
/// [AppDatabase].
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
    /// Only [AppDatabase] calls this.
    synchronized void replaceAll(List<ServiceTemplate> items) {
        templates.clear();
        templates.addAll(items);
        sort(templates);
    }

    /// Recurrence rules have no natural order once they are more than a
    /// weekday, so templates sort by the fields that still do: time of day,
    /// then location, then id for a stable order between equal rows.
    private static void sort(List<ServiceTemplate> list) {
        list.sort(Comparator.comparing(ServiceTemplate::time)
                .thenComparing(ServiceTemplate::location)
                .thenComparing(ServiceTemplate::id));
    }
}
