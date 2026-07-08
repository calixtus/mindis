/**
 * JavaFX desktop application module. Thin UI adapter over org.mindis.core
 * (PLAN.md section 2.5). Open module: FXMLLoader and FxmlKit need reflective
 * access to views and controllers.
 */
open module org.mindis.gui {
    requires org.mindis.core;
    requires org.mindis.workbench;

    requires javafx.controls;
    requires javafx.fxml;
    requires com.dlsc.fxmlkit;
    requires com.dlsc.gemsfx;
    requires atlantafx.base;

    requires io.avaje.inject;
    requires jakarta.inject;
    requires ai.timefold.solver.core;
    requires java.logging;

    // Binds the slf4j-api pulled in transitively (avaje-inject et al) to
    // java.util.logging; never referenced directly from mindis code.
    requires org.slf4j.jul;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.gui.GuiModule;
}
