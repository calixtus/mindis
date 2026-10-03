package org.mindis.gui.modules;

import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.stage.Window;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.LiturgicalServices;
import org.mindis.core.planning.ServiceArchiver;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.gui.planning.PlanExportChooser;
import org.mindis.gui.planning.PlanningViewModel;
import org.mindis.gui.data.LiveStore;

/// The services screen's whole-plan actions - archiving and exporting - which
/// act on the live service list rather than on the selected row.
final class ServicePlanActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicePlanActions.class);

    private final PlanningViewModel planningViewModel;
    private final LiveStore<LiturgicalService> serviceStore;

    ServicePlanActions(PlanningViewModel planningViewModel, LiveStore<LiturgicalService> serviceStore) {
        this.planningViewModel = planningViewModel;
        this.serviceStore = serviceStore;
    }

    /// Freezes live services up to `cutoff` into self-contained archived
    /// snapshots and removes them from the live list (saving the document
    /// commits both). Returns whether anything was archived. Supplied to the
    /// Archived Plans dialog as its archive action.
    boolean archive(LocalDate cutoff) {
        ServiceArchiver.Result result = planningViewModel.archive(cutoff);
        if (result.isEmpty()) {
            return false;
        }
        Map<String, LiturgicalService> byId = new HashMap<>();
        serviceStore.items().forEach(service -> byId.put(service.id(), service));
        for (String id : result.removedServiceIds()) {
            LiturgicalService service = byId.get(id);
            if (service != null) {
                // Removing the row from the store updates the table on its own.
                serviceStore.remove(service);
            }
        }
        return true;
    }

    /// Exports the services dated within `[from, to]` (a blank bound is
    /// unbounded) after asking for the target file.
    void export(Window owner, @Nullable LocalDate from, @Nullable LocalDate to, PlanExportFormat preferredFormat) {
        List<LiturgicalService> services = LiturgicalServices.inDateRange(serviceStore.items(), from, to);
        if (services.isEmpty()) {
            LOGGER.info(Localization.lang("Nothing to export"));
            return;
        }
        Optional<PlanExportChooser.Target> target = PlanExportChooser.show(
                owner, planningViewModel, "MinDis", preferredFormat);
        if (target.isEmpty()) {
            return;
        }
        PlanExportFormat format = target.get().format();
        try {
            planningViewModel.exportLive(services, target.get().file(), format);
            LOGGER.info(Localization.lang("%0 saved to %1", format.name(), target.get().file().getFileName()));
        } catch (UncheckedIOException e) {
            LOGGER.error(Localization.lang("%0 export failed: %1", format.name(), e.getMessage()), e);
        }
    }
}
