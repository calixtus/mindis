package org.mindis.core.export;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.ServiceType;

/// The data a plan template works with: values, not sentences.
///
/// <p>Dates arrive as `java.time` values and are formatted by the template
/// (`{{ service.dateTime | date("EEEE, d. MMMM yyyy") }}`), an unfilled slot
/// arrives as `assigned = false` rather than as a `-`, and headings are the
/// template's to compose. Nothing here is a decision the application made
/// about how the document should read.
///
/// <p>Everything is plain maps, lists, strings, numbers and `java.time`
/// values: the template contract does not move when internal types do, no
/// package has to be opened for reflection, and a template expression cannot
/// reach an object that does anything.
final class PlanTemplateModel {

    private PlanTemplateModel() {
    }

    static Map<String, Object> build(List<Service> services, ParishIdentity parish) {
        List<Service> sorted = new ArrayList<>(services);
        sorted.sort(Comparator.comparing(Service::dateTime));

        List<Map<String, Object>> serviceModels = new ArrayList<>();
        Map<String, Long> countByServer = new LinkedHashMap<>();
        for (Service service : sorted) {
            List<Map<String, Object>> slots = new ArrayList<>();
            long open = 0;
            for (Slot slot : service.slots()) {
                boolean assigned = slot.serverName() != null;
                slots.add(mapOf(
                        "role", slot.roleId(),
                        "roleName", slot.roleName(),
                        "serverName", assigned ? slot.serverName() : "",
                        "assigned", assigned));
                if (assigned) {
                    countByServer.merge(slot.serverName(), 1L, Long::sum);
                } else {
                    open++;
                }
            }
            serviceModels.add(mapOf(
                    "dateTime", service.dateTime(),
                    "date", service.dateTime().toLocalDate(),
                    "time", service.dateTime().toLocalTime(),
                    "type", service.type().name(),
                    "typeLabel", EnumDisplay.of(service.type()),
                    "location", service.location(),
                    "slots", slots,
                    "slotCount", (long) slots.size(),
                    "openCount", open));
        }

        List<Map<String, Object>> servers = countByServer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> mapOf("name", entry.getKey(), "count", entry.getValue()))
                .toList();

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("services", serviceModels);
        model.put("servers", servers);
        model.put("parish", mapOf(
                "name", parish.name(),
                "hasName", !parish.name().isEmpty(),
                "hasLogo", parish.logoPng() != null,
                "logo", PlanTemplate.LOGO_DESTINATION));
        model.put("range", range(sorted));
        model.put("generatedAt", LocalDateTime.now());
        // The application's own translations, for a template that should read
        // in whatever language the application is set to.
        model.put("labels", labels());
        return model;
    }

    private static Map<String, Object> range(List<Service> sorted) {
        if (sorted.isEmpty()) {
            return mapOf("hasServices", false);
        }
        LocalDate from = sorted.getFirst().dateTime().toLocalDate();
        LocalDate to = sorted.getLast().dateTime().toLocalDate();
        return mapOf("hasServices", true, "from", from, "to", to, "count", (long) sorted.size());
    }

    /// The column and section wordings the built-in template uses, so a
    /// template that keeps them stays translated; a template that wants its own
    /// wording just writes it, or calls `lang("...")` for any other
    /// translation.
    private static Map<String, Object> labels() {
        return mapOf(
                "plan", Localization.lang("Altar server plan"),
                "services", Localization.lang("Services"),
                "role", Localization.lang("Role"),
                "server", Localization.lang("Server"),
                "count", Localization.lang("Count"),
                "assignmentsPerServer", Localization.lang("Assignments per server"));
    }

    /// `Map.of` reorders and rejects nulls; a template model wants neither.
    private static Map<String, Object> mapOf(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /// One service as the export sees it, whether it came from the live roster
    /// or from an archived snapshot.
    record Service(LocalDateTime dateTime, ServiceType type, String location, List<Slot> slots) {

        Service {
            slots = List.copyOf(slots);
        }

        LocalTime time() {
            return dateTime.toLocalTime();
        }
    }

    /// One role slot; `serverName` is null while nobody is assigned to it.
    record Slot(String roleId, String roleName, @Nullable String serverName) {
    }
}
