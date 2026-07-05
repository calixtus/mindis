package org.mindis.gui.hello;

import jakarta.inject.Singleton;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import org.mindis.core.service.GreetingService;

/**
 * Controller for the M0 spike view. Constructor-injected via Avaje Inject,
 * proving the core-to-gui DI chain on the module path.
 */
@Singleton
public class HelloController {

    private final GreetingService greetingService;

    @FXML
    private Label greetingLabel;

    public HelloController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @FXML
    private void initialize() {
        greetingLabel.setText(greetingService.welcomeMessage());
    }
}
