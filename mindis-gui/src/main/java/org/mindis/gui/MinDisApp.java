package org.mindis.gui;

import atlantafx.base.theme.PrimerLight;

import com.dlsc.fxmlkit.fxml.FxmlKit;

import io.avaje.inject.BeanScope;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.mindis.core.l10n.Localization;
import org.mindis.gui.di.AvajeDiAdapter;
import org.mindis.gui.hello.HelloView;

/**
 * Application entry point. Owns the Avaje {@link BeanScope} (single scope per
 * application, PLAN.md section 2.4) and bridges it into FxmlKit's DI hook.
 */
public class MinDisApp extends Application {

    private BeanScope beanScope;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        beanScope = BeanScope.builder().build();
        FxmlKit.setDiAdapter(new AvajeDiAdapter(beanScope));
        FxmlKit.setResourceBundle(Localization.getBundle());

        setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Scene scene = new Scene(new HelloView(), 480, 320);
        stage.setTitle(Localization.lang("MinDis - Minister Dispatcher"));
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (beanScope != null) {
            beanScope.close();
        }
    }
}
