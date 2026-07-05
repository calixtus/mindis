/**
 * UI-agnostic core of MinDis: domain model, services, persistence, localization.
 * Must never require any javafx module (PLAN.md section 2.5) - a future web
 * module builds on this one.
 */
module org.mindis.core {
    exports org.mindis.core.l10n;
    exports org.mindis.core.service;

    requires java.logging;
    requires io.avaje.inject;
    requires jakarta.inject;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.core.service.ServiceModule;
}
