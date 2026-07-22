package org.mindis.core.persistence;

import java.util.List;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.ServiceTemplate;

/// Everything one parish's planning data consists of, as a single JSON
/// document: the four live entity lists plus the frozen archive. This is the
/// unit the user opens and saves - there is no per-entity file and no implicit
/// storage location any more; a document lives wherever the user put it.
///
/// <p>Null-tolerant like the model records ({@link Server}, {@link
/// org.mindis.core.model.Slot}): a list absent from an older or hand-edited
/// file reads as empty rather than failing the whole open.
public record MinDisDocument(
        int version,
        List<Role> roles,
        List<Server> servers,
        List<ServiceTemplate> templates,
        List<LiturgicalService> services,
        List<ArchivedService> archivedServices) {

    public static final int CURRENT_VERSION = 1;

    public MinDisDocument {
        roles = copyOrEmpty(roles);
        servers = copyOrEmpty(servers);
        templates = copyOrEmpty(templates);
        services = copyOrEmpty(services);
        archivedServices = copyOrEmpty(archivedServices);
    }

    /// A document with no data at all - the starting point of {@link
    /// AppDatabase#newDocument()}, which seeds the default roles into it.
    public static MinDisDocument empty() {
        return new MinDisDocument(CURRENT_VERSION, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static <T> List<T> copyOrEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
