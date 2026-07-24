package org.mindis.gui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.model.CollectionMeta.LogoBackground;

/// Edits a collection's identity - its display name (the parish name), its logo
/// and the backdrop behind it. The logo is either a custom PNG (stored inside
/// the document as Base64, so PNG-only and size-capped to keep the document
/// small) or, when there is none, a stock icon from {@link LogoIcons}. A
/// low-contrast logo can be lifted onto a light or dark rounded backdrop.
final class CollectionMetaDialog extends Dialog<CollectionMeta> {

    private static final int PREVIEW_SIZE = 72;
    private static final int MAX_LOGO_BYTES = 512 * 1024;

    private final TextField nameField = new TextField();
    private final StackPane preview = new StackPane();
    private final ComboBox<String> iconChoice = new ComboBox<>();
    private final ChoiceBox<LogoBackground> backgroundChoice = new ChoiceBox<>();
    private @Nullable String logoBase64;

    CollectionMetaDialog(CollectionMeta meta, @Nullable Window owner) {
        initOwner(owner);
        setTitle(Localization.lang("Edit collection..."));
        this.logoBase64 = meta.logoPngBase64();

        nameField.setPromptText(Localization.lang("Collection name"));
        nameField.setText(meta.displayName() == null ? "" : meta.displayName());

        configureIconChoice(meta.logoIcon());
        configureBackgroundChoice(meta.logoBackground());

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
        HBox logoButtons = new HBox(8, choose, remove);
        logoButtons.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.add(new Label(Localization.lang("Name")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(Localization.lang("Logo")), 0, 1);
        grid.add(new HBox(12, preview, logoButtons), 1, 1);
        grid.add(new Label(Localization.lang("Icon")), 0, 2);
        grid.add(iconChoice, 1, 2);
        grid.add(new Label(Localization.lang("Background")), 0, 3);
        grid.add(backgroundChoice, 1, 3);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private void configureIconChoice(@Nullable String selected) {
        iconChoice.getItems().setAll(LogoIcons.CATALOG);
        iconChoice.setValue(selected);
        iconChoice.setPromptText(Localization.lang("Default icon"));
        iconChoice.setButtonCell(iconCell());
        iconChoice.setCellFactory(list -> iconCell());
        iconChoice.valueProperty().addListener((observable, old, value) -> updatePreview());
    }

    private static ListCell<String> iconCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(@Nullable String literal, boolean empty) {
                super.updateItem(literal, empty);
                if (empty || literal == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(LogoIcons.displayName(literal));
                    setGraphic(LogoIcons.iconNode(literal, 18));
                }
            }
        };
    }

    private void configureBackgroundChoice(LogoBackground selected) {
        backgroundChoice.getItems().setAll(LogoBackground.values());
        backgroundChoice.setValue(selected);
        backgroundChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(@Nullable LogoBackground background) {
                if (background == null) {
                    return "";
                }
                return switch (background) {
                    case NONE -> Localization.lang("No background");
                    case LIGHT -> Localization.lang("Light");
                    case DARK -> Localization.lang("Dark");
                };
            }

            @Override
            public LogoBackground fromString(String string) {
                return LogoBackground.NONE;
            }
        });
        backgroundChoice.valueProperty().addListener((observable, old, value) -> updatePreview());
    }

    private CollectionMeta result() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        LogoBackground background = backgroundChoice.getValue() == null
                ? LogoBackground.NONE
                : backgroundChoice.getValue();
        return new CollectionMeta(name.isBlank() ? null : name, logoBase64,
                iconChoice.getValue(), background);
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
        Node content;
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(PREVIEW_SIZE);
            view.setFitHeight(PREVIEW_SIZE);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            content = view;
        } else {
            String literal = iconChoice.getValue() == null ? LogoIcons.DEFAULT : iconChoice.getValue();
            content = LogoIcons.iconNode(literal, PREVIEW_SIZE - 24);
        }
        preview.getChildren().setAll(content);
        LogoBackground background = backgroundChoice.getValue() == null
                ? LogoBackground.NONE
                : backgroundChoice.getValue();
        preview.setStyle(CollectionSwitcher.logoBackgroundStyle(background));
        // A custom image wins over a stock icon, so the icon picker is only
        // relevant when there is no image.
        iconChoice.setDisable(image != null);
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
