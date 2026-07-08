package org.mindis.gui.modules;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mindis.core.model.Role;
import org.mindis.core.model.Server;
import org.mindis.core.model.UnavailabilityPeriod;
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

    List<String> csvHeader() {
        return List.of("id", "firstName", "lastName", "contact", "birthDate", "familyId",
                "qualifications", "unavailabilities", "preferredTimes", "experienced", "active");
    }

    List<String> toCsvRow(Server server) {
        return List.of(
                server.id(),
                server.firstName(),
                server.lastName(),
                server.contact(),
                server.birthDate() == null ? "" : server.birthDate().toString(),
                server.familyId() == null ? "" : server.familyId(),
                server.qualifications().stream().map(this::roleName).sorted()
                        .collect(Collectors.joining(", ")),
                formatUnavailabilities(server.unavailabilities()),
                formatPreferredTimes(server.preferredTimes()),
                String.valueOf(server.experienced()),
                String.valueOf(server.active()));
    }

    /** Blank first+last name rows are skipped; a blank id gets a fresh one, matching {@link #createStub()}. */
    Server fromCsvRow(List<String> row) {
        String firstName = CsvFields.at(row, 1);
        String lastName = CsvFields.at(row, 2);
        if (firstName.isEmpty() && lastName.isEmpty()) {
            return null;
        }
        String id = CsvFields.at(row, 0);
        String familyId = CsvFields.at(row, 5);
        String active = CsvFields.at(row, 10);
        return new Server(
                id.isEmpty() ? Server.newId() : id,
                firstName,
                lastName,
                CsvFields.at(row, 3),
                CsvFields.parseDate(CsvFields.at(row, 4)),
                familyId.isEmpty() ? null : familyId,
                parseQualifications(CsvFields.at(row, 6)),
                parseUnavailabilities(CsvFields.at(row, 7)),
                parsePreferredTimes(CsvFields.at(row, 8)),
                Boolean.parseBoolean(CsvFields.at(row, 9)),
                active.isEmpty() || Boolean.parseBoolean(active));
    }

    /** Role names, matched case-insensitively; unmatched names are dropped. */
    private Set<String> parseQualifications(String text) {
        Set<String> ids = new HashSet<>();
        if (text.isEmpty()) {
            return ids;
        }
        List<Role> roles = roleRepository.findAll();
        for (String name : text.split(",")) {
            String trimmed = name.strip();
            roles.stream()
                    .filter(role -> role.name().equalsIgnoreCase(trimmed))
                    .findFirst()
                    .ifPresent(role -> ids.add(role.id()));
        }
        return ids;
    }

    private static String formatUnavailabilities(List<UnavailabilityPeriod> periods) {
        return periods.stream()
                .map(period -> period.start() + "/" + period.end())
                .collect(Collectors.joining(", "));
    }

    /** {@code "2026-01-01/2026-01-10, ..."}; malformed or inverted entries are dropped. */
    private static List<UnavailabilityPeriod> parseUnavailabilities(String text) {
        List<UnavailabilityPeriod> periods = new ArrayList<>();
        if (text.isEmpty()) {
            return periods;
        }
        for (String part : text.split(",")) {
            String[] bounds = part.strip().split("/", 2);
            if (bounds.length != 2) {
                continue;
            }
            LocalDate start = CsvFields.parseDate(bounds[0].strip());
            LocalDate end = CsvFields.parseDate(bounds[1].strip());
            if (start != null && end != null && !end.isBefore(start)) {
                periods.add(new UnavailabilityPeriod(start, end));
            }
        }
        return periods;
    }
}
