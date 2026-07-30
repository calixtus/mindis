/// JavaFX desktop application module. Thin UI adapter over org.mindis.core
/// (PLAN.md section 2.5).
///
/// Views are built in Java, so the module is closed apart from one hole: the
/// JavaFX launcher reflectively instantiates the [javafx.application.Application]
/// subclass, so the package holding `MinDisApp` is opened to `javafx.graphics`
/// and to nothing else.
module org.mindis.gui {
    requires org.mindis.core;
    requires org.jspecify;

    opens org.mindis.gui to javafx.graphics;

    requires javafx.controls;
    requires com.dlsc.gemsfx;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;

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
