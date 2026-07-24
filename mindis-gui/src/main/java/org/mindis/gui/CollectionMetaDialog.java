package org.mindis.gui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.model.CollectionMeta.LogoBackground;
import org.mindis.gui.theme.AccentColorSelector;

/// Edits a collection's identity - its display name (the parish name), its logo
/// and the backdrop behind it.
///
/// <p>Logo and icon are one control: clicking the logo tile opens a popover of
/// stock icons ({@link LogoIcons}) with a "custom image" button at the bottom.
/// Picking an icon or an image replaces the other, so there is no separate
/// remove action. A custom image is stored inside the document as a Base64 PNG
/// (PNG-only, size-capped to keep the document small).
///
/// <p>The backdrop is a row of swatches like the settings accent picker: light,
/// dark, or transparent (a square with a diagonal line) - to lift a low-contrast
/// logo off the sidebar.
final class CollectionMetaDialog extends Dialog<CollectionMeta> {

    private static final int PREVIEW_TILE = 56;
    private static final int PREVIEW_IMAGE_FIT = 48;
    private static final int PREVIEW_GLYPH = 30;
    private static final int PICKER_GLYPH = 24;
    private static final int MAX_LOGO_BYTES = 512 * 1024;

    private final TextField nameField = new TextField();
    private final StackPane preview = new StackPane();
    private final ContextMenu picker = new ContextMenu();
    private final ToggleGroup backgroundGroup = new ToggleGroup();

    private @Nullable String logoBase64;
    private @Nullable String logoIcon;
    private LogoBackground background;

    CollectionMetaDialog(CollectionMeta meta, @Nullable Window owner) {
        initOwner(owner);
        setTitle(Localization.lang("Edit collection..."));
        this.logoBase64 = meta.logoPngBase64();
        this.logoIcon = meta.logoIcon();
        this.background = meta.logoBackground();

        nameField.setPromptText(Localization.lang("Collection name"));
        nameField.setText(meta.displayName() == null ? "" : meta.displayName());

        preview.setPrefSize(PREVIEW_TILE, PREVIEW_TILE);
        preview.setMinSize(PREVIEW_TILE, PREVIEW_TILE);
        preview.setMaxSize(PREVIEW_TILE, PREVIEW_TILE);
        preview.setAlignment(Pos.CENTER);

        Button logoButton = new Button();
        logoButton.setGraphic(preview);
        logoButton.getStyleClass().add("collection-logo-button");
        logoButton.setTooltip(new Tooltip(Localization.lang("Change logo")));
        buildPicker();
        logoButton.setOnAction(event -> picker.show(logoButton, Side.BOTTOM, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.add(new Label(Localization.lang("Name")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(Localization.lang("Logo")), 0, 1);
        grid.add(logoButton, 1, 1);
        grid.add(new Label(Localization.lang("Background")), 0, 2);
        grid.add(backgroundSwatches(), 1, 2);

        getDialogPane().getStylesheets().add(
                AccentColorSelector.class.getResource("accent-selector.css").toExternalForm());
        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        updatePreview();
        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private void buildPicker() {
        FlowPane grid = new FlowPane(6, 6);
        grid.setPrefWrapLength(5 * (PICKER_GLYPH + 18));
        grid.setPadding(new Insets(4));
        for (String literal : LogoIcons.CATALOG) {
            Button icon = new Button();
            icon.setGraphic(LogoIcons.iconNode(literal, PICKER_GLYPH));
            icon.setTooltip(new Tooltip(LogoIcons.displayName(literal)));
            icon.setOnAction(event -> {
                logoBase64 = null;
                logoIcon = literal;
                updatePreview();
                picker.hide();
            });
            grid.getChildren().add(icon);
        }

        Button custom = new Button(Localization.lang("Select custom image..."));
        custom.setMaxWidth(Double.MAX_VALUE);
        custom.setOnAction(event -> {
            picker.hide();
            chooseLogo();
        });

        CustomMenuItem gridItem = new CustomMenuItem(grid, false);
        CustomMenuItem customItem = new CustomMenuItem(custom, false);
        picker.getItems().setAll(gridItem, new SeparatorMenuItem(), customItem);
    }

    private HBox backgroundSwatches() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                swatch(LogoBackground.LIGHT, Localization.lang("Light"),
                        "-fx-background-color: white; -fx-border-color: -color-border-default;"
                                + " -fx-border-width: 1; -fx-border-radius: 5;"),
                swatch(LogoBackground.DARK, Localization.lang("Dark"),
                        "-fx-background-color: #2b2b2b;"),
                // Transparent: a bordered square with a diagonal line drawn by a gradient.
                swatch(LogoBackground.NONE, Localization.lang("Transparent"),
                        "-fx-background-color: linear-gradient(to top right, transparent 44%,"
                                + " -color-danger-emphasis 44%, -color-danger-emphasis 56%,"
                                + " transparent 56%); -fx-border-color: -color-border-default;"
                                + " -fx-border-width: 1; -fx-border-radius: 5;"));

        backgroundGroup.selectedToggleProperty().subscribe((oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    backgroundGroup.selectToggle(oldToggle);
                }
                return;
            }
            background = (LogoBackground) newToggle.getUserData();
            updatePreview();
        });
        return row;
    }

    private ToggleButton swatch(LogoBackground value, String tooltip, String fillStyle) {
        ToggleButton button = new ToggleButton();
        button.setUserData(value);
        button.getStyleClass().add("accent-swatch");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setToggleGroup(backgroundGroup);
        Region fill = new Region();
        fill.getStyleClass().add("swatch-fill");
        fill.setStyle(fillStyle);
        button.setGraphic(fill);
        button.setSelected(value == background);
        return button;
    }

    private CollectionMeta result() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        return new CollectionMeta(name.isBlank() ? null : name, logoBase64, logoIcon, background);
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
            view.setFitWidth(PREVIEW_IMAGE_FIT);
            view.setFitHeight(PREVIEW_IMAGE_FIT);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            content = view;
        } else {
            content = LogoIcons.iconNode(logoIcon == null ? LogoIcons.DEFAULT : logoIcon, PREVIEW_GLYPH);
        }
        preview.getChildren().setAll(content);
        preview.setStyle(CollectionSwitcher.logoBackgroundStyle(background));
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
