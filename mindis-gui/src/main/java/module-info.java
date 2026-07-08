/**
 * JavaFX desktop application module. Thin UI adapter over org.mindis.core
 * (PLAN.md section 2.5). Open module: FXMLLoader and FxmlKit need reflective
 * access to views and controllers.
 */
open module org.mindis.gui {
    requires org.mindis.core;
    requires org.mindis.workbench;
    requires org.jspecify;

    requires javafx.controls;
    requires javafx.fxml;
    requires com.dlsc.fxmlkit;
    requires com.dlsc.gemsfx;
    requires atlantafx.base;

    requires io.avaje.inject;
    requires jakarta.inject;
    requires ai.timefold.solver.core;
    requires org.slf4j;

    // java.logging is for org.mindis.gui.logging.AlertOnErrorHandler (a JUL
    // Handler) and registering it on the JUL root logger; everything else in
    // this module logs through org.slf4j.
    requires java.logging;

    // Binds org.slf4j (both mindis's own calls and every slf4j-emitting
    // third-party library, e.g. avaje-inject) into java.util.logging, so
    // console/file output all lands in the same JUL handlers
    // (org.mindis.core.logging.LoggingBootstrap). Runtime-only: nothing in
    // mindis code references this module's types directly.
    requires org.slf4j.jul;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.gui.GuiModule;
}
