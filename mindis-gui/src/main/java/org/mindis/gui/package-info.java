/**
 * Declares to Avaje Inject that beans under these {@code org.mindis.core}
 * packages are provided by another module (the core module) at runtime. This
 * makes the gui generator detect the core module as external and suppresses the
 * bulk of its cross-module "unsatisfied requires" note under JPMS. One residual
 * informational note from the avaje generator remains (a known limitation with
 * split JPMS modules); it does not affect the build or runtime wiring, which is
 * driven by {@code CoreModule.providesBeans()}.
 */
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
