/**
 * UI-agnostic core of MinDis: domain model, services, persistence, localization.
 * Must never require any javafx module (PLAN.md section 2.5) - a future web
 * module builds on this one.
 */
module org.mindis.core {
    exports org.mindis.core.l10n;
    exports org.mindis.core.model;
    exports org.mindis.core.persistence;
    exports org.mindis.core.planning;
    exports org.mindis.core.preferences;
    exports org.mindis.core.service;

    requires java.logging;
    requires io.avaje.inject;
    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires ai.timefold.solver.core;

    opens org.mindis.core.model to com.fasterxml.jackson.databind;
    opens org.mindis.core.planning to ai.timefold.solver.core;
    opens org.mindis.core.preferences to com.fasterxml.jackson.databind;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.core.CoreModule;
}
