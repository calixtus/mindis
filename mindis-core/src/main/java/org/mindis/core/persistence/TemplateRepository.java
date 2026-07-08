package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.preferences.DataDirectory;

/**
 * Recurring-service template storage: templates.json in the user data
 * directory. Upsert by id.
 */
@Singleton
public class TemplateRepository {

    private final JsonStore<ServiceTemplate> store;
    private @Nullable List<ServiceTemplate> templates;

    public TemplateRepository(DataDirectory dataDirectory) {
        this(dataDirectory.resolve("templates.json"));
    }

    TemplateRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<ServiceTemplate> findAll() {
        return List.copyOf(cached());
    }

    public synchronized void save(ServiceTemplate template) {
        List<ServiceTemplate> list = cached();
        list.removeIf(existing -> existing.id().equals(template.id()));
        list.add(template);
        sort(list);
        store.save(list);
    }

    public synchronized void delete(String id) {
        List<ServiceTemplate> list = cached();
        list.removeIf(existing -> existing.id().equals(id));
        store.save(list);
    }

    /** The live (mutable) cache, loading and sorting it from disk on first access. */
    private List<ServiceTemplate> cached() {
        if (templates == null) {
            templates = new ArrayList<>(store.load());
            sort(templates);
        }
        return templates;
    }

    private static void sort(List<ServiceTemplate> list) {
        list.sort(Comparator.comparing(ServiceTemplate::dayOfWeek).thenComparing(ServiceTemplate::time));
    }
}
