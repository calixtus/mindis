package org.mindis.gui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.CollectionMeta;

/// Edits a collection's identity - its display name (the parish name) and an
/// optional logo. The logo is stored inside the document as a Base64 PNG (see
/// {@link CollectionMeta}), so only PNG files are accepted and, to keep the
/// document small, their size is capped.
final class CollectionMetaDialog extends Dialog<CollectionMeta> {

    private static final int PREVIEW_SIZE = 72;
    private static final int MAX_LOGO_BYTES = 512 * 1024;

    private final TextField nameField = new TextField();
    private final StackPane preview = new StackPane();
    private @Nullable String logoBase64;

    CollectionMetaDialog(CollectionMeta meta, @Nullable Window owner) {
        initOwner(owner);
        setTitle(Localization.lang("Edit collection..."));
        this.logoBase64 = meta.logoPngBase64();

        nameField.setPromptText(Localization.lang("Collection name"));
        nameField.setText(meta.displayName() == null ? "" : meta.displayName());

        preview.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);
        preview.setMinSize(PREVIEW_SIZE, PREVIEW_SIZE);
        preview.setAlignment(Pos.CENTER);
        updatePreview();

        Button choose = new Button(Localization.lang("Choose image..."));
        choose.setOnAction(event -> chooseLogo());
        Button remove = new Button(Localization.lang("Remove logo"));
        remove.setOnAction(event -> {
            logoBase64 = null;
            updatePreview();
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.add(new Label(Localization.lang("Name")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(Localization.lang("Logo")), 0, 1);
        grid.add(new HBox(10, preview, new HBox(8, choose, remove) {{
            setAlignment(Pos.CENTER_LEFT);
        }}), 1, 1);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private CollectionMeta result() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        return new CollectionMeta(name.isBlank() ? null : name, logoBase64);
    }

    private void chooseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.lang("Choose image..."));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Localization.lang("PNG image"), "*.png"));
        File selected = chooser.showOpenDialog(getOwner());
        if (selected == null) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(selected.toPath());
        } catch (IOException e) {
            error(Localization.lang("Could not open %0", selected.getName()));
            return;
        }
        if (bytes.length > MAX_LOGO_BYTES) {
            error(Localization.lang("Image is too large (maximum %0 KB).", MAX_LOGO_BYTES / 1024));
            return;
        }
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError()) {
            error(Localization.lang("Could not open %0", selected.getName()));
            return;
        }
        logoBase64 = Base64.getEncoder().encodeToString(bytes);
        updatePreview();
    }

    private void updatePreview() {
        Image image = decode(logoBase64);
        if (image == null) {
            FontIcon placeholder = new FontIcon("mdi2c-church");
            placeholder.setIconSize(PREVIEW_SIZE - 16);
            preview.getChildren().setAll(placeholder);
            return;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(PREVIEW_SIZE);
        view.setFitHeight(PREVIEW_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        preview.getChildren().setAll(view);
    }

    private static @Nullable Image decode(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            return image.isError() ? null : image;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(getOwner());
        alert.showAndWait();
    }
}
