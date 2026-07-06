package org.mindis.gui.modules;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.Localization;
import org.mindis.workbench.WorkbenchModule;

/**
 * About screen (modeled on JabRef's Help &gt; About dialog): logo, version,
 * a one-line "copy version info" for bug reports, and links to the
 * repository and license.
 */
public class AboutModule extends WorkbenchModule {

    private static final String REPOSITORY_URL = "https://github.com/calixtus/mindis";
    private static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";

    private final HostServices hostServices;

    public AboutModule(String name, HostServices hostServices) {
        super(name, "mdi2i-information-outline");
        this.hostServices = hostServices;
    }

    @Override
    public Node activate() {
        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/org/mindis/gui/icons/app-icon/mindis-128.png")));
        logo.setFitWidth(96);
        logo.setFitHeight(96);
        logo.setPreserveRatio(true);

        Label title = new Label("MinDis");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label tagline = new Label(Localization.lang("Minister Dispatcher: altar server planning"));
        Label version = new Label(Localization.lang("Version %0", readVersion()));

        Hyperlink repositoryLink = new Hyperlink(REPOSITORY_URL);
        repositoryLink.setOnAction(e -> hostServices.showDocument(REPOSITORY_URL));

        Hyperlink licenseLink = new Hyperlink(Localization.lang("Licensed under the Apache License 2.0"));
        licenseLink.setOnAction(e -> hostServices.showDocument(LICENSE_URL));

        Button copyButton = new Button(Localization.lang("Copy version information"));
        copyButton.setOnAction(e -> copyVersionInfo());

        VBox content = new VBox(10, logo, title, tagline, version, repositoryLink, licenseLink, copyButton);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(32));
        content.setMaxWidth(420);

        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }

    private void copyVersionInfo() {
        String info = "MinDis %s\nJava %s\nJavaFX %s\nOS %s %s".formatted(
                readVersion(),
                System.getProperty("java.version"),
                System.getProperty("javafx.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"));
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(info);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private String readVersion() {
        try (InputStream in = getClass().getResourceAsStream("/org/mindis/gui/about/version.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }
}
