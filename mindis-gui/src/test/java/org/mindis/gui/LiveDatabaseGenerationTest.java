package org.mindis.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;

/// The signal that tells the shell to rebuild the screen: it has to fire when the
/// document's contents are replaced and stay quiet when they are merely written
/// out, because a module that reads a repository as it builds itself is rebuilt in
/// response and doing that on every save would throw away the user's place.
///
/// Runs on the FX thread: a document action touches the archive repository, whose
/// change listener hops to that thread, so there has to be one.
class LiveDatabaseGenerationTest {

    /// Each case runs whole on the FX thread; see the class comment.
    private static void onFxThread(ThrowingBody body) throws InterruptedException {
        FxTest.runAndWait(() -> {
            try {
                body.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private interface ThrowingBody {
        void run() throws IOException;
    }

    private final RoleRepository roles = new RoleRepository();
    private final ServerRepository servers = new ServerRepository();
    private final TemplateRepository templates = new TemplateRepository();
    private final ServiceRepository services = new ServiceRepository();
    private final ArchivedServiceRepository archived = new ArchivedServiceRepository();
    private final AppDatabase database =
            new AppDatabase(roles, servers, templates, services, archived);
    private final LiveDatabase liveDatabase =
            new LiveDatabase(database, roles, servers, templates, services, archived);

    private int generation() {
        return liveDatabase.documentGenerationProperty().get();
    }

    @Test
    void aNewDocumentIsANewGeneration() throws InterruptedException {
        onFxThread(() -> {
            int before = generation();
            liveDatabase.newDocument();
            assertEquals(before + 1, generation());
        });
    }

    @Test
    void openingAFileIsANewGeneration(@TempDir Path directory) throws InterruptedException {
        onFxThread(() -> {
            Path file = directory.resolve("parish.mindis");
            liveDatabase.newDocument();
            liveDatabase.saveAs(file);

            int before = generation();
            liveDatabase.open(file);
            assertEquals(before + 1, generation());
        });
    }

    @Test
    void revertingIsANewGeneration(@TempDir Path directory) throws InterruptedException {
        onFxThread(() -> {
            liveDatabase.newDocument();
            liveDatabase.saveAs(directory.resolve("parish.mindis"));

            int before = generation();
            liveDatabase.reload();
            assertEquals(before + 1, generation());
        });
    }

    /// The case the signal exists to exclude: saving writes the same data back out,
    /// so nothing on screen is stale and nothing should be rebuilt over it.
    @Test
    void savingIsNotANewGeneration(@TempDir Path directory) throws InterruptedException {
        onFxThread(() -> {
            Path file = directory.resolve("parish.mindis");
            liveDatabase.newDocument();
            liveDatabase.saveAs(file);

            int afterSaveAs = generation();
            liveDatabase.save();
            assertEquals(afterSaveAs, generation());

            liveDatabase.saveAs(directory.resolve("copy.mindis"));
            assertEquals(afterSaveAs, generation());
        });
    }
}
