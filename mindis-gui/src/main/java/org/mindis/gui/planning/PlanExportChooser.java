package org.mindis.gui.planning;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.mindis.core.export.PlanExportFormat;
import org.mindis.core.l10n.Localization;

/// Shared "save plan as..." {@link FileChooser} setup for {@link org.mindis.gui.modules.ServicesModule}
/// and {@link ArchivedPlansDialog} - both offer the same {@link PlanExportFormat} choices and used to
/// resolve the export format from the typed file name only, silently ignoring whichever format the
/// user had actually picked (e.g. from a format menu) whenever it disagreed with the initial filter.
public final class PlanExportChooser {

    private static final List<FileChooser.ExtensionFilter> FILTERS = List.of(
            new FileChooser.ExtensionFilter("PDF", "*.pdf"),
            new FileChooser.ExtensionFilter("CSV", "*.csv"),
            new FileChooser.ExtensionFilter("TXT", "*.txt"),
            new FileChooser.ExtensionFilter("RTF", "*.rtf"),
            new FileChooser.ExtensionFilter("Markdown", "*.md"));

    private PlanExportChooser() {
    }

    public record Target(Path file, PlanExportFormat format) {
    }

    /// Prompts for a save file, its extension filter preselected to {@code preferredFormat} so the
    /// dialog agrees with whatever format the user already chose to trigger it. Empty if cancelled.
    public static Optional<Target> show(Window owner, PlanningViewModel viewModel, String initialFileNameBase,
            PlanExportFormat preferredFormat) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.lang("Export plan"));
        chooser.getExtensionFilters().addAll(FILTERS);
        chooser.setSelectedExtensionFilter(filterFor(preferredFormat));
        viewModel.lastExportDirectory()
                .map(Path::toFile)
                .filter(File::isDirectory)
                .ifPresent(chooser::setInitialDirectory);
        chooser.setInitialFileName(initialFileNameBase + "." + preferredFormat.extension());
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return Optional.empty();
        }
        viewModel.rememberExportDirectory(target.getParentFile().toPath());
        PlanExportFormat format = PlanningViewModel.resolveFormat(
                target.getName(), chooser.getSelectedExtensionFilter().getExtensions());
        return Optional.of(new Target(target.toPath(), format));
    }

    private static FileChooser.ExtensionFilter filterFor(PlanExportFormat format) {
        for (FileChooser.ExtensionFilter filter : FILTERS) {
            if (filter.getExtensions().getFirst().equals("*." + format.extension())) {
                return filter;
            }
        }
        throw new IllegalArgumentException("No filter for format: " + format);
    }
}
