package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.mindis.core.model.ServiceTemplate;
import org.mindis.core.preferences.AppDirectories;

/**
 * Recurring-service template storage: templates.json in the user data
 * directory. Upsert by id.
 */
@Singleton
public class TemplateRepository {

    private final JsonStore<ServiceTemplate> store;
    private List<ServiceTemplate> templates;

    public TemplateRepository() {
        this(AppDirectories.userDataDir().resolve("templates.json"));
    }

    TemplateRepository(Path file) {
        this.store = new JsonStore<>(file, new TypeReference<>() {
        });
    }

    public synchronized List<ServiceTemplate> findAll() {
        if (templates == null) {
            templates = new ArrayList<>(store.load());
            sort();
        }
        return List.copyOf(templates);
    }

    public synchronized void save(ServiceTemplate template) {
        findAll();
        templates.removeIf(existing -> existing.id().equals(template.id()));
        templates.add(template);
        sort();
        store.save(templates);
    }

    public synchronized void delete(String id) {
        findAll();
        templates.removeIf(existing -> existing.id().equals(id));
        store.save(templates);
    }

    private void sort() {
        templates.sort(Comparator.comparing(ServiceTemplate::dayOfWeek).thenComparing(ServiceTemplate::time));
    }
}
