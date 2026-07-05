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
    requires atlantafx.base;

    requires io.avaje.inject;
    requires jakarta.inject;

    provides io.avaje.inject.spi.InjectExtension with org.mindis.gui.hello.HelloModule;
}
