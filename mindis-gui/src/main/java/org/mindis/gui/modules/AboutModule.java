package org.mindis.gui.modules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
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
import javafx.scene.text.Text;

import atlantafx.base.theme.Styles;
import org.kordamp.ikonli.javafx.FontIcon;

import org.jspecify.annotations.Nullable;
import org.mindis.core.l10n.Localization;
import org.mindis.core.update.AppVersion;
import org.mindis.gui.logging.LogConsoleModel;
import org.mindis.gui.logging.LogEntry;
import org.mindis.gui.shell.ShellModule;

/// About screen (modeled on JabRef's Help &gt; About dialog): logo, version,
/// maintainers, links to the repository and license, a copyable version-info
/// block for bug reports, and - at the bottom - an in-app log messages panel
/// (severity-colored log history, so a user can see and copy what went wrong
/// without digging into the log file).
public final class AboutModule extends ShellModule {

    private static final String REPOSITORY_URL = "https://github.com/calixtus/mindis";
    private static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";
    private static final DateTimeFormatter ENTRY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /// Below this, the logo+text side-by-side layout no longer fits comfortably - stack
    /// instead.
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
        super(name, "mdi2i-information-outline", "mdi2i-information");
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
        Label version = new Label(Localization.lang("Version %0", AppVersion.currentText()));
        Label maintainers = new Label(Localization.lang("Maintainers: %0", readMaintainers()));
        maintainers.setWrapText(true);

        Hyperlink repositoryLink = new Hyperlink(REPOSITORY_URL);
        repositoryLink.setOnAction(e -> hostServices.showDocument(REPOSITORY_URL));

        Hyperlink licenseLink = new Hyperlink(Localization.lang("Licensed under the Apache License 2.0"));
        licenseLink.setOnAction(e -> hostServices.showDocument(LICENSE_URL));

        Hyperlink noticesLink = new Hyperlink(Localization.lang("Third-party licenses"));
        noticesLink.setOnAction(e -> showThirdPartyNotices());

        Node versionInfoBox = buildVersionInfoBox();

        VBox textBlock = new VBox(10, title, tagline, version, maintainers,
                repositoryLink, licenseLink, noticesLink, versionInfoBox);
        textBlock.setAlignment(Pos.CENTER_LEFT);

        Node aboutInfo = buildResponsiveAboutInfo(logo, textBlock);

        VBox aboutInfoWrapper = new VBox(aboutInfo);
        aboutInfoWrapper.setAlignment(Pos.CENTER);

        Node logMessages = buildLogMessages();

        VBox root = new VBox(12, aboutInfoWrapper, logMessages);
        root.setPadding(new Insets(12));
        VBox.setVgrow(logMessages, Priority.ALWAYS);
        return root;
    }

    /// Logo + text side by side (logo on the right) while there's room;
    /// stacked (logo on top, text below) once the available width drops
    /// below [#NARROW_WIDTH]. JavaFX has no CSS media queries, so this
    /// reparents `logo`/`textBlock` between two prebuilt
    /// containers on a width listener instead - a `FlowPane` would
    /// wrap automatically, but it can't put the logo on a *different* side
    /// depending on which state it's in (wide: text left, logo right;
    /// narrow: logo first/top), since wrapping preserves child order either way.
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

    /// A bordered, scrolling block showing [#buildVersionInfo()],
    /// with a copy icon that only appears on hover - same interaction as
    /// [LogEntryCell]. A `TextArea` (tried first) always reserves
    /// scrollbar space even when its content fits, and fighting that via
    /// scrollbar CSS is more fragile than just not using a scrollable control
    /// for text that was never meant to scroll.
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

    private Node buildLogMessages() {
        Label header = new Label(Localization.lang("Log messages"));
        header.setStyle("-fx-font-weight: bold;");

        ListView<LogEntry> list = new ListView<>(logConsole.entries());
        // By identity: two identical messages logged in the same second are still two rows.
        Set<LogEntry> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
        list.setCellFactory(view -> new LogEntryCell(view.getItems(), expanded));
        list.setPrefHeight(160);

        VBox box = new VBox(6, header, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    /// Shown in the About screen's copyable version-info box - bug reports need this, not just
    /// a clipboard side effect.
    private String buildVersionInfo() {
        return "MinDis %s\nJava %s\nJavaFX %s\nOS %s %s".formatted(
                AppVersion.currentText(),
                System.getProperty("java.version"),
                System.getProperty("javafx.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"));
    }

    /// Shows the notices for everything distributed alongside MinDis. The file
    /// is generated at build time from the resolved runtime dependencies (see
    /// `org.mindis.gradle.check.licenses`), so what the user reads here is
    /// what actually shipped.
    private void showThirdPartyNotices() {
        TextArea notices = new TextArea(readThirdPartyNotices());
        notices.setEditable(false);
        notices.setWrapText(true);
        notices.setPrefSize(720, 520);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(Localization.lang("Third-party licenses"));
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(notices);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private String readThirdPartyNotices() {
        try (InputStream in = getClass().getResourceAsStream("/org/mindis/gui/about/THIRD-PARTY-NOTICES.md")) {
            if (in == null) {
                return Localization.lang("The third-party notices are missing from this build");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Localization.lang("The third-party notices are missing from this build");
        }
    }

    /// The repo-root `MAINTAINERS` file, copied into this module's
    /// resources at build time (see `build.gradle.kts`) rather than
    /// duplicated by hand, so it's always in sync with GitHub's own
    /// `MAINTAINERS` convention.
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

    /// One log line: `HH:mm:ss [LEVEL] message`, text color by
    /// severity, with icon buttons that only appear on hover - always-visible
    /// buttons on every row would be noisier than the list itself.
    ///
    /// <p>A message too long for the row is cut off with an ellipsis rather than
    /// widening the list, and the hover buttons then include a chevron that expands
    /// the row to show it whole, wrapped. Line breaks would make a collapsed row
    /// taller, so collapsed they are shown as spaces, and a message that has any is
    /// expandable even when it fits.
    private static final class LogEntryCell extends ListCell<LogEntry> {
        private final ObservableList<LogEntry> entries;
        /// The entries expanded in this list - kept by the list rather than the cell,
        /// since cells are reused for other entries as the list scrolls.
        private final Set<LogEntry> expanded;
        private final Label text = new Label();
        private final Button expandButton = new Button(null, new FontIcon("mdi2c-chevron-down"));
        private final HBox actions;
        private final HBox row;

        LogEntryCell(ObservableList<LogEntry> entries, Set<LogEntry> expanded) {
            this.entries = entries;
            this.expanded = expanded;

            // Asking for no width of its own keeps the list from growing a horizontal
            // scroll bar to fit the longest message: every cell gets the list's width.
            setPrefWidth(0);

            text.setTextOverrun(OverrunStyle.ELLIPSIS);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);

            expandButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            expandButton.setOnAction(e -> toggleExpanded());
            expandButton.managedProperty().bind(expandButton.visibleProperty());

            Button copyButton = new Button(null, new FontIcon("mdi2c-content-copy"));
            copyButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            copyButton.setOnAction(e -> copyToClipboard(entryText()));

            Button removeButton = new Button(null, new FontIcon("mdi2c-close"));
            removeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            removeButton.setOnAction(e -> remove());

            actions = new HBox(2, expandButton, copyButton, removeButton);
            actions.setMinWidth(Region.USE_PREF_SIZE);
            actions.setVisible(false);
            actions.setManaged(false);

            row = new HBox(6, text, actions);
            row.setAlignment(Pos.CENTER_LEFT);
            // A Labeled lays its graphic out at the graphic's own preferred width, so
            // without this the row would be as wide as its text and never ellipsize.
            row.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> getWidth() - getInsets().getLeft() - getInsets().getRight(),
                    widthProperty(), insetsProperty()));

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
            text.setStyle("-fx-text-fill: " + colorFor(entry.level()) + ";");
            showExpanded(expanded.contains(entry));
            setGraphic(row);
        }

        /// The row's height at the width it is given, which a wrapped message needs:
        /// the default asks the graphic for its height without one, which is a single line.
        @Override
        protected double computePrefHeight(double width) {
            if (getGraphic() != row) {
                return super.computePrefHeight(width);
            }
            double rowWidth = (width < 0 ? getWidth() : width) - snappedLeftInset() - snappedRightInset();
            return snappedTopInset() + row.prefHeight(rowWidth) + snappedBottomInset();
        }

        /// Whether the chevron is offered is only known once the row has a width.
        /// Measured against the row with all three buttons showing, as on hover, so
        /// it does not change while the pointer moves in and the text makes room.
        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            LogEntry entry = getItem();
            if (entry == null || getGraphic() != row) {
                return;
            }
            boolean expandable = expanded.contains(entry) || hasLineBreaks(entry) || overflows(entry);
            if (expandButton.isVisible() != expandable) {
                expandButton.setVisible(expandable);
            }
        }

        private boolean overflows(LogEntry entry) {
            // A Text node, not a second Label: a Label outside the scene has no skin and
            // measures nothing.
            Text probe = new Text(collapsedText(entry));
            probe.setFont(text.getFont());
            double available = row.getWidth() - row.getSpacing() - allActionsWidth()
                    - text.getPadding().getLeft() - text.getPadding().getRight();
            return probe.getLayoutBounds().getWidth() > available;
        }

        private double allActionsWidth() {
            double buttons = 0;
            for (Node child : actions.getChildren()) {
                buttons += child.prefWidth(-1);
            }
            return buttons + actions.getSpacing() * (actions.getChildren().size() - 1);
        }

        private void toggleExpanded() {
            LogEntry entry = getItem();
            if (entry == null) {
                return;
            }
            if (!expanded.remove(entry)) {
                // Entries removed or rotated out of the log since are dropped here rather
                // than by a listener on the log, which would outlive this view.
                expanded.removeIf(other -> entries.stream().noneMatch(e -> e == other));
                expanded.add(entry);
            }
            showExpanded(expanded.contains(entry));
            // The cell's height changes with it; the list has to lay its cells out again.
            getListView().refresh();
        }

        private void showExpanded(boolean isExpanded) {
            LogEntry entry = getItem();
            if (entry == null) {
                return;
            }
            text.setWrapText(isExpanded);
            text.setText(isExpanded ? fullText(entry) : collapsedText(entry));
            row.setAlignment(isExpanded ? Pos.TOP_LEFT : Pos.CENTER_LEFT);
            ((FontIcon) expandButton.getGraphic())
                    .setIconLiteral(isExpanded ? "mdi2c-chevron-up" : "mdi2c-chevron-down");
        }

        private static String fullText(LogEntry entry) {
            return "%s [%s] %s".formatted(ENTRY_TIME_FORMAT.format(entry.time()), entry.level(), entry.message());
        }

        private static String collapsedText(LogEntry entry) {
            return fullText(entry).replaceAll("\\s*\\R\\s*", " ");
        }

        private static boolean hasLineBreaks(LogEntry entry) {
            return entry.message().strip().lines().count() > 1;
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
