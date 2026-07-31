package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;

import org.mindis.core.model.ServiceTemplate;

/// Recurring-service template storage (see [InMemoryRepository]).
///
/// Recurrence rules have no natural order once they are more than a weekday, so
/// templates sort by the fields that still do: time of day, then location, then
/// id for a stable order between equal rows.
@Singleton
public final class TemplateRepository extends InMemoryRepository<ServiceTemplate> {

    public TemplateRepository() {
        super(ServiceTemplate::id, Comparator.comparing(ServiceTemplate::time)
                .thenComparing(ServiceTemplate::location)
                .thenComparing(ServiceTemplate::id));
    }
}
