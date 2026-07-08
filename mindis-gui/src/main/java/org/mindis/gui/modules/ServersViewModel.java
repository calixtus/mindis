package org.mindis.gui.modules;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;

/**
 * ViewModel for {@link ServersModule}: owns every repository call and the
 * preferred-times text &lt;-&gt; {@code Set<LocalTime>} parsing/formatting, so the
 * module only constructs UI and binds to this class.
 */
final class ServersViewModel {

    private final ServerRepository serverRepository;
    private final RoleRepository roleRepository;

    ServersViewModel(ServerRepository serverRepository, RoleRepository roleRepository) {
        this.serverRepository = serverRepository;
        this.roleRepository = roleRepository;
    }

    List<Server> findAll() {
        return serverRepository.findAll();
    }

    void save(Server server) {
        serverRepository.save(server);
    }

    void delete(Server server) {
        serverRepository.delete(server.id());
    }

    /** A blank, active, inexperienced server, for the New action. */
    Server createStub() {
        return new Server(Server.newId(), "", "", "", null, null,
                Set.of(), List.of(), Set.of(), false, true);
    }

    /** Roles available for the qualifications checklist. */
    List<Role> findAllRoles() {
        return roleRepository.findAll();
    }

    /** Display name for a role id, falling back to the id if the role was deleted. */
    String roleName(String roleId) {
        return roleRepository.findById(roleId).map(Role::name).orElse(roleId);
    }

    /** Parses "10:00, 18:30" style input; unparsable entries are dropped. */
    Set<LocalTime> parsePreferredTimes(String text) {
        Set<LocalTime> times = new HashSet<>();
        for (String part : text.split(",")) {
            try {
                times.add(LocalTime.parse(part.strip(), DateTimeFormatter.ofPattern("H:mm")));
            } catch (DateTimeParseException e) {
                // Ignore invalid entries; the field is free-form.
            }
        }
        return times;
    }

    String formatPreferredTimes(Set<LocalTime> times) {
        return times.stream()
                .sorted()
                .map(LocalTime::toString)
                .collect(Collectors.joining(", "));
    }
}
