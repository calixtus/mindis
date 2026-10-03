package org.mindis.gui.shell;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.persistence.CsvIO;
import org.mindis.core.persistence.CsvRowMapper;

/// The file side of a [CrudModule]'s CSV import and export: asks for the file,
/// runs [CsvIO] on it and reports the outcome in the window's overlays. Holds no
/// state of its own beyond the overlays it reports to.
final class CsvFileTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvFileTransfer.class);

    private final ShellOverlays overlays;

    CsvFileTransfer(ShellOverlays overlays) {
        this.overlays = overlays;
    }

    /// Prompts for a target file and writes `items` to it through `mapper`.
    <T> void export(Window owner, String title, CsvRowMapper<T> mapper, List<T> items) {
        FileChooser chooser = chooser(title);
        chooser.setInitialFileName(title + ".csv");
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(target.toPath())) {
            CsvIO.write(writer, mapper, items);
        } catch (IOException e) {
            LOGGER.warn("CSV export failed: {}", target, e);
            overlays.dialogs().showError(title, e.getMessage(), e);
        }
    }

    /// Prompts for a source file, hands every row `mapper` reads from it to
    /// `merge`, then posts `summaryMessage.apply(imported, total)`.
    <T> void importInto(Window owner, String title, CsvRowMapper<T> mapper, Consumer<List<T>> merge,
                        BiFunction<Integer, Integer, String> summaryMessage) {
        File source = chooser(title).showOpenDialog(owner);
        if (source == null) {
            return;
        }
        try {
            CsvIO.Import<T> imported = CsvIO.read(Files.readString(source.toPath()), mapper);
            merge.accept(imported.items());
            // A count of imported rows is an outcome, not a question - post it
            // to the info center instead of blocking on an OK button.
            overlays.notify(title, summaryMessage.apply(imported.items().size(), imported.rowCount()));
        } catch (IOException e) {
            LOGGER.warn("CSV import failed: {}", source, e);
            overlays.dialogs().showError(title, e.getMessage(), e);
        }
    }

    private static FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return chooser;
    }
}
