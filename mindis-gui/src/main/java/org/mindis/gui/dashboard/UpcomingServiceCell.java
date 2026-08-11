package org.mindis.gui.dashboard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.mindis.core.l10n.EnumDisplay;
import org.mindis.core.l10n.Localization;
import org.mindis.gui.dashboard.DashboardViewModel.UpcomingService;
import org.mindis.gui.util.DateTimes;

/// One row of the "next services" widget: when and what on the first line, how
/// far off and how well staffed on the second. The fill state is the point of
/// the widget, so it is a bar plus "n/m" rather than a number buried in a
/// sentence, and a service nobody is assigned to yet is coloured as a problem
/// while a fully staffed one is played down.
final class UpcomingServiceCell extends ListCell<UpcomingService> {

    private final Label when = new Label();
    private final Label what = new Label();
    private final Label relative = new Label();
    private final Label fill = new Label();
    private final ProgressBar staffing = new ProgressBar();
    private final VBox layout;

    UpcomingServiceCell() {
        when.getStyleClass().add("dashboard-service-when");
        what.getStyleClass().add("dashboard-service-what");
        relative.getStyleClass().add("dashboard-service-relative");
        fill.getStyleClass().add("dashboard-service-fill");
        staffing.getStyleClass().add("dashboard-service-bar");
        staffing.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(6, when, what, spacer, relative);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(staffing, Priority.ALWAYS);
        HBox bottom = new HBox(8, staffing, fill);
        bottom.setAlignment(Pos.CENTER_LEFT);

        layout = new VBox(4, top, bottom);
        layout.getStyleClass().add("dashboard-service-row");
        setGraphic(layout);
    }

    @Override
    protected void updateItem(UpcomingService service, boolean empty) {
        super.updateItem(service, empty);
        getStyleClass().removeAll("dashboard-service-unstaffed", "dashboard-service-complete");
        if (empty || service == null) {
            setGraphic(null);
            return;
        }
        setGraphic(layout);

        LocalDate date = service.dateTime().toLocalDate();
        when.setText(DateTimes.weekday(date) + " " + DateTimes.dateTime(service.dateTime()));
        what.setText(service.location().isBlank()
                ? EnumDisplay.of(service.type())
                : EnumDisplay.of(service.type()) + " - " + service.location());
        relative.setText(relativeDay(date));

        int total = service.totalSlots();
        int assigned = service.assignedSlots();
        boolean hasSlots = total > 0;
        staffing.setVisible(hasSlots);
        staffing.setManaged(hasSlots);
        staffing.setProgress(hasSlots ? (double) assigned / total : 0);
        fill.setText(hasSlots ? assigned + "/" + total : Localization.lang("No slots"));
        if (hasSlots && assigned == 0) {
            getStyleClass().add("dashboard-service-unstaffed");
        } else if (hasSlots && assigned == total) {
            getStyleClass().add("dashboard-service-complete");
        }
    }

    /// "Today"/"Tomorrow"/"in n days" - the distance is what tells the planner
    /// whether an unstaffed service is urgent.
    private static String relativeDay(LocalDate date) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (days <= 0) {
            return Localization.lang("Today");
        }
        if (days == 1) {
            return Localization.lang("Tomorrow");
        }
        return Localization.lang("in %0 days", String.valueOf(days));
    }
}
