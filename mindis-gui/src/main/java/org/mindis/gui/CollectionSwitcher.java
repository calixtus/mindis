package org.mindis.gui;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import org.mindis.core.l10n.Localization;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.preferences.RecentCollection;

/// The sidebar-top collection switcher (the account-switcher pattern from the
/// sidebar UX review): shows the open collection's logo and name, a save button
/// enabled only while there is work to save, and a dropdown that switches to a
/// recent collection or runs the document actions (Open other, Save as, Edit
/// collection, New collection).
///
/// <p>All data lives in one document = one collection (a parish), so switching
/// collection is opening a file - the dropdown is where {@link DocumentSession}'s
/// file actions live now, in place of the old global toolbar.
///
/// <p>A {@link MenuButton} hosts the dropdown (it opens reliably on click). Its
/// natural minimum width - driven by the dropdown arrow - is neutralized with
/// {@code minWidth 0} so it never overflows the collapsed icon rail into the
/// sidebar divider, and on the rail the arrow is hidden by CSS so only the logo
/// shows, like an icon-only nav button.
public final class CollectionSwitcher extends HBox {

    private static final int HEADER_LOGO_SIZE = 26;
    /// Matches the module nav buttons' icon size, so the collapsed switcher is
    /// the same height as the icon-only nav rail below it.
    private static final int RAIL_LOGO_SIZE = 18;
    private static final int MENU_LOGO_SIZE = 20;

    private final DocumentSession session;
    private final LiveDatabase liveDatabase;
    private final ObservableBooleanValue solving;

    private final MenuButton collectionButton = new MenuButton();
    private final Label nameLabel = new Label();
    private final Label dirtyDot = new Label("●");
    private final StackPane logoHolder = new StackPane();
    private final HBox identity;
    private final Button saveButton = new Button();

    private boolean collapsed;
    private CollectionMeta currentMeta;

    public CollectionSwitcher(DocumentSession session, LiveDatabase liveDatabase,
                              ObservableBooleanValue solving) {
        this.session = session;
        this.liveDatabase = liveDatabase;
        this.solving = solving;
        getStyleClass().add("workbench-collection-switcher");
        setAlignment(Pos.CENTER_LEFT);

        nameLabel.getStyleClass().add("collection-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.textProperty().bind(Bindings.createStringBinding(session::collectionDisplayName,
                liveDatabase.metaProperty(), liveDatabase.documentPathProperty()));

        dirtyDot.getStyleClass().add("collection-dirty-dot");
        dirtyDot.visibleProperty().bind(liveDatabase.dirtyProperty());
        dirtyDot.managedProperty().bind(dirtyDot.visibleProperty());

        logoHolder.getStyleClass().add("collection-logo");
        logoHolder.setAlignment(Pos.CENTER);
        currentMeta = liveDatabase.meta();
        updateLogo();
        liveDatabase.metaProperty().subscribe(meta -> {
            currentMeta = meta;
            updateLogo();
        });

        identity = new HBox(6, logoHolder, nameLabel, dirtyDot);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        collectionButton.setGraphic(identity);
        collectionButton.getStyleClass().add("collection-button");
        collectionButton.setMaxWidth(Double.MAX_VALUE);
        // The dropdown arrow otherwise forces a minimum width wider than the
        // 60px rail, overflowing into the divider; let it shrink instead.
        collectionButton.setMinWidth(0);
        collectionButton.setTooltip(new Tooltip(Localization.lang("Switch collection")));
        // Recents change with every open/save (and the menu content depends on
        // the collapsed state), so rebuild it each time it opens.
        collectionButton.setOnShowing(event -> rebuildMenu());

        saveButton.setGraphic(new FontIcon("mdi2c-content-save"));
        saveButton.getStyleClass().add("collection-save-button");
        saveButton.setTooltip(new Tooltip(Localization.lang("Save") + " (Ctrl+S)"));
        saveButton.disableProperty().bind(liveDatabase.dirtyProperty().not().or(solving));
        saveButton.setOnAction(event -> session.onSave());

        HBox.setHgrow(collectionButton, Priority.ALWAYS);
        getChildren().setAll(collectionButton, saveButton);
    }

    /// Follows the sidebar's collapsed state: on the icon-only rail only the
    /// logo shows (name, dropdown arrow, dirty dot and the inline save button
    /// hide), and Save moves into the dropdown instead.
    public void bindCollapsed(ObservableValue<? extends Boolean> collapsed) {
        collapsed.subscribe(this::applyCollapsed);
    }

    private void applyCollapsed(boolean value) {
        this.collapsed = value;
        nameLabel.setVisible(!value);
        nameLabel.setManaged(!value);
        saveButton.setVisible(!value);
        saveButton.setManaged(!value);
        // Center the lone logo on the rail; left-align logo + name when expanded.
        identity.setAlignment(value ? Pos.CENTER : Pos.CENTER_LEFT);
        // Resize the logo to the nav-rail icon size (and back) so the collapsed
        // switcher matches the module buttons' height.
        updateLogo();
        // The dirty dot is redundant next to the (hidden) name on the rail; the
        // window title still carries the asterisk.
        dirtyDot.visibleProperty().unbind();
        if (value) {
            dirtyDot.setVisible(false);
        } else {
            dirtyDot.visibleProperty().bind(liveDatabase.dirtyProperty());
        }
        // Drives CSS that hides the dropdown arrow and centers the logo.
        pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), value);
    }

    private void rebuildMenu() {
        collectionButton.getItems().clear();
        // On the rail the inline save button is hidden, so Save lives here.
        if (collapsed) {
            MenuItem save = actionItem("mdi2c-content-save", Localization.lang("Save"),
                    session::onSave);
            save.setDisable(!liveDatabase.isDirty() || solving.get());
            collectionButton.getItems().addAll(save, new SeparatorMenuItem());
        }

        Path current = liveDatabase.documentPath().orElse(null);
        boolean addedRecent = false;
        for (RecentCollection recent : session.recents()) {
            if (current != null && current.toAbsolutePath().toString().equals(recent.path())) {
                continue;
            }
            if (!addedRecent) {
                MenuItem header = new MenuItem(Localization.lang("Recent collections"));
                header.setDisable(true);
                collectionButton.getItems().add(header);
                addedRecent = true;
            }
            collectionButton.getItems().add(recentItem(recent));
        }
        if (addedRecent) {
            collectionButton.getItems().add(new SeparatorMenuItem());
        }

        MenuItem openOther = actionItem("mdi2f-folder-open", Localization.lang("Open other..."),
                session::onOpen);
        MenuItem saveAs = actionItem("mdi2c-content-save-edit", Localization.lang("Save as..."),
                session::onSaveAs);
        saveAs.setDisable(solving.get());
        MenuItem edit = actionItem("mdi2p-pencil", Localization.lang("Edit collection..."),
                this::openMetadataEditor);
        MenuItem newCollection = actionItem("mdi2p-plus", Localization.lang("New collection"),
                session::onNew);
        collectionButton.getItems().addAll(openOther, saveAs, edit,
                new SeparatorMenuItem(), newCollection);
    }

    private MenuItem recentItem(RecentCollection recent) {
        MenuItem item = new MenuItem(recentLabel(recent));
        Image logo = imageFromBase64(recent.logoPngBase64());
        item.setGraphic(logo == null
                ? churchIcon(MENU_LOGO_SIZE)
                : logoView(logo, MENU_LOGO_SIZE));
        item.setOnAction(event -> session.switchTo(recent));
        return item;
    }

    private static String recentLabel(RecentCollection recent) {
        String displayName = recent.displayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return Path.of(recent.path()).getFileName().toString();
    }

    private static MenuItem actionItem(String iconLiteral, String text, Runnable action) {
        MenuItem item = new MenuItem(text, new FontIcon(iconLiteral));
        item.setOnAction(event -> action.run());
        return item;
    }

    private void openMetadataEditor() {
        Optional<CollectionMeta> edited = new CollectionMetaDialog(session.currentMeta(), owner())
                .showAndWait();
        edited.ifPresent(session::updateMetadata);
    }

    private void updateLogo() {
        int size = collapsed ? RAIL_LOGO_SIZE : HEADER_LOGO_SIZE;
        Image logo = imageFromBase64(currentMeta.logoPngBase64());
        logoHolder.getChildren().setAll(logo == null
                ? churchIcon(size)
                : logoView(logo, size));
    }

    private static ImageView logoView(Image image, int size) {
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static FontIcon churchIcon(int size) {
        FontIcon icon = new FontIcon("mdi2c-church");
        icon.setIconSize(size);
        icon.getStyleClass().add("collection-logo-placeholder");
        return icon;
    }

    /// Decodes a Base64 PNG logo into an image, or {@code null} when there is
    /// none or it cannot be decoded (a bad cached thumbnail must never break the
    /// switcher).
    private static @Nullable Image imageFromBase64(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image image = new Image(new ByteArrayInputStream(bytes));
            return image.isError() ? null : image;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private @Nullable Window owner() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
