package org.mindis.core.persistence;

import jakarta.inject.Singleton;

import java.util.Comparator;

import org.mindis.core.model.LiturgicalService;

/// Service storage: the liturgical services of the currently open document,
/// assignments included (an assignment lives on its slot), ordered by date and
/// time (see [InMemoryRepository]).
@Singleton
public final class ServiceRepository extends InMemoryRepository<LiturgicalService> {

    public ServiceRepository() {
        super(LiturgicalService::id, Comparator.comparing(LiturgicalService::dateTime));
    }
}
