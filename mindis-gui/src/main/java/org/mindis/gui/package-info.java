/// Declares to Avaje Inject that beans under these `org.mindis.core`
/// packages are provided by another module (the core module) at runtime. This
/// makes the gui generator detect the core module as external and suppresses the
/// bulk of its cross-module "unsatisfied requires" note under JPMS.
///
/// One "unsatisfied requires" note remains, naming core beans that other core
/// beans depend on. It is an avaje-inject generator bug: when reading an
/// external module (`ExternalProvider.addOtherModuleProvides`) it records the
/// module's internal dependencies as `requires` without removing its own
/// `provides`, so `FactoryOrder` sees `CoreModule` waiting on itself. The note
/// affects neither the build nor runtime wiring, which is driven by
/// `CoreModule.providesBeans()`.
@InjectModule(requiresPackages = {
        RoleRepository.class,     // org.mindis.core.persistence
        PreferencesService.class, // org.mindis.core.preferences
        PlanExportService.class,  // org.mindis.core.export
        PlanningService.class     // org.mindis.core.planning
})
@NullMarked
package org.mindis.gui;

import io.avaje.inject.InjectModule;

import org.jspecify.annotations.NullMarked;

import org.mindis.core.export.PlanExportService;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.planning.PlanningService;
import org.mindis.core.preferences.PreferencesService;
