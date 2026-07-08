package org.mindis.gui.modules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javafx.application.HostServices;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.Reflection;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import atlantafx.base.theme.Styles;
import org.kordamp.ikonli.javafx.FontIcon;

import org.jspecify.annotations.Nullable;
import org.mindis.core.l10n.Localization;
import org.mindis.gui.logging.LogConsoleModel;
import org.mindis.gui.logging.LogEntry;
import org.mindis.workbench.WorkbenchModule;

/**
 * About screen (modeled on JabRef's Help &gt; About dialog): logo, version,
 * maintainers, links to the repository and license, a copyable version-info
 * block for bug reports, and - at the bottom - an in-app error console
 * (severity-colored log history, so a user can see and copy what went wrong
 * without digging into the log file).
 */
public class AboutModule extends WorkbenchModule {

    private static final String REPOSITORY_URL = "https://github.com/calixtus/mindis";
    private static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";
    private static final DateTimeFormatter ENTRY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Below this, the logo+text side-by-side layout no longer fits comfortably - stack instead. */
    private static final double NARROW_WIDTH = 480;

    private static final String VERSION_BOX_STYLE = """
            -fx-background-color: -color-bg-subtle;
            -fx-border-color: -color-border-default;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            """;
    private static final String VERSION_BOX_STYLE_HOVER = """
            -fx-background-color: -color-bg-default;
            -fx-border-color: -color-accent-emphasis;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            """;

    private final HostServices hostServices;
    private final LogConsoleModel logConsole;

    public AboutModule(String name, HostServices hostServices, LogConsoleModel logConsole) {
        super(name, "mdi2i-information-outline");
        this.hostServices = hostServices;
        this.logConsole = logConsole;
    }

    @Override
    public Node activate() {
        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/org/mindis/gui/icons/app-icon/mindis-128.png")));
        // 1.5x the original 96px - the reflection was invisible at 96px
        // because the VBox below it packed the title label right up against
        // it (10px spacing vs. the effect's own ~14px), so the title's own
        // background painted over it; sitting next to the text block instead
        // of stacked above it gives the reflection clear space underneath.
        logo.setFitWidth(144);
        logo.setFitHeight(144);
        logo.setPreserveRatio(true);
        // Only fraction set, like JabRef's own <Reflection fraction="0.15"/> -
        // the 4-arg constructor (tried first) requires topOpacity/bottomOpacity
        // too, and passing 0 for both (instead of the class's own defaults,
        // 0.5 and 0.0) made the whole reflection fully transparent, i.e.
        // invisible regardless of fraction.
        Reflection reflection = new Reflection();
        reflection.setFraction(0.15);
        logo.setEffect(reflection);

        Label title = new Label("MinDis");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label tagline = new Label(Localization.lang("Minister Dispatcher: altar server planning"));
        Label version = new Label(Localization.lang("Version %0", readVersion()));
        Label maintainers = new Label(Localization.lang("Maintainers: %0", readMaintainers()));
        maintainers.setWrapText(true);

        Hyperlink repositoryLink = new Hyperlink(REPOSITORY_URL);
        repositoryLink.setOnAction(e -> hostServices.showDocument(REPOSITORY_URL));

        Hyperlink licenseLink = new Hyperlink(Localization.lang("Licensed under the Apache License 2.0"));
        licenseLink.setOnAction(e -> hostServices.showDocument(LICENSE_URL));

        Node versionInfoBox = buildVersionInfoBox();

        VBox textBlock = new VBox(10, title, tagline, version, maintainers,
                repositoryLink, licenseLink, versionInfoBox);
        textBlock.setAlignment(Pos.CENTER_LEFT);

        Node aboutInfo = buildResponsiveAboutInfo(logo, textBlock);

        VBox aboutInfoWrapper = new VBox(aboutInfo);
        aboutInfoWrapper.setAlignment(Pos.CENTER);

        Node errorConsole = buildErrorConsole();

        VBox root = new VBox(12, aboutInfoWrapper, errorConsole);
        root.setPadding(new Insets(12));
        VBox.setVgrow(errorConsole, Priority.ALWAYS);
        return root;
    }

    /**
     * Logo + text side by side (logo on the right) while there's room;
     * stacked (logo on top, text below) once the available width drops
     * below {@link #NARROW_WIDTH}. JavaFX has no CSS media queries, so this
     * reparents {@code logo}/{@code textBlock} between two prebuilt
     * containers on a width listener instead - a {@code FlowPane} would
     * wrap automatically, but it can't put the logo on a *different* side
     * depending on which state it's in (wide: text left, logo right;
     * narrow: logo first/top), since wrapping preserves child order either way.
     */
    private Node buildResponsiveAboutInfo(Node logo, Node textBlock) {
        HBox wideLayout = new HBox(28);
        wideLayout.setAlignment(Pos.CENTER);

        VBox narrowLayout = new VBox(16);
        narrowLayout.setAlignment(Pos.CENTER);

        StackPane holder = new StackPane();
        holder.setPadding(new Insets(32));
        holder.setMaxWidth(560);

        Runnable relayout = () -> {
            boolean narrow = holder.getWidth() > 0 && holder.getWidth() < NARROW_WIDTH;
            wideLayout.getChildren().clear();
            narrowLayout.getChildren().clear();
            if (narrow) {
                narrowLayout.getChildren().addAll(logo, textBlock);
                holder.getChildren().setAll(narrowLayout);
            } else {
                wideLayout.getChildren().addAll(textBlock, logo);
                holder.getChildren().setAll(wideLayout);
            }
        };
        holder.widthProperty().addListener((obs, oldWidth, newWidth) -> relayout.run());
        relayout.run();
        return holder;
    }

    /**
     * A bordered, scrolling block showing {@link #buildVersionInfo()},
     * with a copy icon that only appears on hover - same interaction as
     * {@link LogEntryCell}. A {@code TextArea} (tried first) always reserves
     * scrollbar space even when its content fits, and fighting that via
     * scrollbar CSS is more fragile than just not using a scrollable control
     * for text that was never meant to scroll.
     */
    private Node buildVersionInfoBox() {
        Label text = new Label(buildVersionInfo());
        text.setWrapText(true);

        Button copyButton = new Button(null, new FontIcon("mdi2c-content-copy"));
        copyButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        copyButton.setOnAction(e -> copyToClipboard(buildVersionInfo()));
        copyButton.setVisible(false);
        copyButton.setManaged(false);

        StackPane box = new StackPane(new ScrollPane(text), copyButton);
        StackPane.setAlignment(text, Pos.CENTER_LEFT);
        StackPane.setAlignment(copyButton, Pos.TOP_RIGHT);
        box.setPadding(new Insets(10));
        box.setMaxWidth(320);
        box.setStyle(VERSION_BOX_STYLE);

        box.setOnMouseEntered(e -> {
            copyButton.setVisible(true);
            copyButton.setManaged(true);
            box.setStyle(VERSION_BOX_STYLE_HOVER);
        });
        box.setOnMouseExited(e -> {
            copyButton.setVisible(false);
            copyButton.setManaged(false);
            box.setStyle(VERSION_BOX_STYLE);
        });
        return box;
    }

    private static void copyToClipboard(String content) {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(content);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private Node buildErrorConsole() {
        Label header = new Label(Localization.lang("Error console"));
        header.setStyle("-fx-font-weight: bold;");

        ListView<LogEntry> list = new ListView<>(logConsole.entries());
        list.setCellFactory(view -> new LogEntryCell(view.getItems()));
        list.setPrefHeight(160);

        VBox box = new VBox(6, header, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    /** Shown in the About screen's copyable version-info box - bug reports need this, not just a clipboard side effect. */
    private String buildVersionInfo() {
        return "MinDis %s\nJava %s\nJavaFX %s\nOS %s %s".formatted(
                readVersion(),
                System.getProperty("java.version"),
                System.getProperty("javafx.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"));
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

    /**
     * The repo-root {@code MAINTAINERS} file, copied into this module's
     * resources at build time (see {@code build.gradle.kts}) rather than
     * duplicated by hand, so it's always in sync with GitHub's own
     * {@code MAINTAINERS} convention.
     */
    private String readMaintainers() {
        try (InputStream in = getClass().getResourceAsStream("/org/mindis/gui/about/MAINTAINERS")) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip().lines()
                    .collect(Collectors.joining(", "));
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * One log line: {@code HH:mm:ss [LEVEL] message}, text color by
     * severity, with copy/remove icon buttons that only appear on hover -
     * always-visible buttons on every row would be noisier than the list
     * itself.
     */
    private static final class LogEntryCell extends ListCell<LogEntry> {
        private final ObservableList<LogEntry> entries;
        private final Label text = new Label();
        private final HBox actions;
        private final HBox row;

        LogEntryCell(ObservableList<LogEntry> entries) {
            this.entries = entries;

            Button copyButton = new Button(null, new FontIcon("mdi2c-content-copy"));
            copyButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            copyButton.setOnAction(e -> copyToClipboard(entryText()));

            Button removeButton = new Button(null, new FontIcon("mdi2c-close"));
            removeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            removeButton.setOnAction(e -> remove());

            actions = new HBox(2, copyButton, removeButton);
            actions.setVisible(false);
            actions.setManaged(false);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row = new HBox(6, text, spacer, actions);
            row.setAlignment(Pos.CENTER_LEFT);

            setOnMouseEntered(e -> {
                actions.setVisible(true);
                actions.setManaged(true);
            });
            setOnMouseExited(e -> {
                actions.setVisible(false);
                actions.setManaged(false);
            });
        }

        @Override
        protected void updateItem(@Nullable LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            text.setText("%s [%s] %s".formatted(
                    ENTRY_TIME_FORMAT.format(entry.time()), entry.level(), entry.message()));
            text.setStyle("-fx-text-fill: " + colorFor(entry.level()) + ";");
            setGraphic(row);
        }

        private String entryText() {
            LogEntry entry = getItem();
            if (entry == null) {
                return "";
            }
            String stackTrace = entry.stackTrace();
            return "%s [%s] %s - %s%s".formatted(
                    ENTRY_TIME_FORMAT.format(entry.time()), entry.level(), entry.loggerName(), entry.message(),
                    stackTrace != null ? "\n\n" + stackTrace : "");
        }

        private void remove() {
            LogEntry entry = getItem();
            if (entry != null) {
                entries.remove(entry);
            }
        }

        private static String colorFor(Level level) {
            if (level.intValue() >= Level.SEVERE.intValue()) {
                return "-color-danger-fg";
            }
            if (level.intValue() >= Level.WARNING.intValue()) {
                return "-color-warning-fg";
            }
            if (level.intValue() >= Level.INFO.intValue()) {
                return "-color-fg-default";
            }
            return "-color-fg-muted";
        }
    }
}
