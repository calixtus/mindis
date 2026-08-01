package org.mindis.core.planning;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;

/// The plan archive: freezing past services into self-contained snapshots, and
/// reading those snapshots back as solver facts.
///
/// Split from [PlanningService] because archiving is not solving. It runs on
/// the user's schedule rather than the solver's, it writes where solving only
/// reads, and it stays meaningful with no solver in the picture at all - a
/// future export- or report-only consumer wants this and none of
/// [PlanningService]. The dependency runs one way: solving needs the archive
/// for [#priorFromArchived], the archive needs nothing from solving.
@Singleton
public final class ArchiveService {

    private final RoleRepository roleRepository;
    private final ServerRepository serverRepository;
    private final ServiceRepository serviceRepository;
    private final ArchivedServiceRepository archivedServiceRepository;

    public ArchiveService(RoleRepository roleRepository,
                          ServerRepository serverRepository,
                          ServiceRepository serviceRepository,
                          ArchivedServiceRepository archivedServiceRepository) {
        this.roleRepository = roleRepository;
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.archivedServiceRepository = archivedServiceRepository;
    }

    /// Freezes every live service dated on or before `cutoff` into a
    /// self-contained [ArchivedService] snapshot (role/server names resolved
    /// now), persists the snapshots immediately, and returns the ids of the
    /// live services to drop. The caller removes those from the live list and
    /// Save-alls to commit the removal. Empty result if the cutoff freezes
    /// nothing.
    public ServiceArchiver.Result archive(LocalDate cutoff) {
        Map<String, Role> rolesById = new HashMap<>();
        roleRepository.findAll().forEach(role -> rolesById.put(role.id(), role));
        Map<String, Server> serversById = new HashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        ServiceArchiver.Result result = ServiceArchiver.archive(
                serviceRepository.findAll(), cutoff, Instant.now(),
                roleId -> rolesById.containsKey(roleId) ? rolesById.get(roleId).name() : null,
                serverId -> serversById.containsKey(serverId) ? serversById.get(serverId).displayName() : null);
        archivedServiceRepository.addAll(result.archived());
        return result;
    }

    /// Every archived service, newest first.
    public List<ArchivedService> listArchived() {
        return archivedServiceRepository.findAll();
    }

    public void deleteArchived(String id) {
        archivedServiceRepository.delete(id);
    }

    /// [PriorAssignment] facts drawn from the archive: any archived slot whose
    /// service date lies in the
    /// [MinDisConstraintProvider#SPACING_THRESHOLD_DAYS]-day tail immediately
    /// before `earliest` and whose server still exists, so the solver is
    /// penalized for scheduling that server again right up against the frozen
    /// history. Empty when there are no live services to place.
    public List<PriorAssignment> priorFromArchived(@Nullable LocalDate earliest) {
        if (earliest == null) {
            return List.of();
        }
        LocalDate cutoff = earliest.minusDays(MinDisConstraintProvider.SPACING_THRESHOLD_DAYS);
        Map<String, Server> serversById = new HashMap<>();
        serverRepository.findAll().forEach(server -> serversById.put(server.id(), server));
        List<PriorAssignment> result = new ArrayList<>();
        for (ArchivedService archived : archivedServiceRepository.findAll()) {
            LocalDate date = archived.dateTime().toLocalDate();
            if (date.isBefore(cutoff) || !date.isBefore(earliest)) {
                continue;
            }
            for (ArchivedService.ArchivedSlot slot : archived.slots()) {
                if (slot.serverId() == null) {
                    continue;
                }
                Server server = serversById.get(slot.serverId());
                if (server != null) {
                    result.add(new PriorAssignment(date, server));
                }
            }
        }
        return result;
    }
}
