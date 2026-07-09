/// UI-agnostic core of MinDis: domain model, services, persistence, localization.
/// Must never require any javafx module (PLAN.md section 2.5) - a future web
/// module builds on this one.
module org.mindis.core {
    exports org.mindis.core.export;
    exports org.mindis.core.l10n;
    exports org.mindis.core.logging;
    exports org.mindis.core.model;
    exports org.mindis.core.persistence;
    exports org.mindis.core.planning;
    exports org.mindis.core.preferences;

    requires org.jspecify;
    // java.logging is for org.mindis.core.logging.LoggingBootstrap only,
    // which configures the JUL backend itself; everything else in this
    // module logs through org.slf4j.
    requires java.logging;
    requires org.slf4j;
    requires io.avaje.inject;
    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires ai.timefold.solver.core;
    requires com.github.librepdf.openpdf;

    opens org.mindis.core.model to com.fasterxml.jackson.databind;
    opens org.mindis.core.planning to ai.timefold.solver.core, com.fasterxml.jackson.databind;
    opens org.mindis.core.preferences to com.fasterxml.jackson.databind;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.core.CoreModule;
}
