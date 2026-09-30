package org.mindis.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;

import org.jspecify.annotations.Nullable;

import org.mindis.core.preferences.PreferencesService;

/// Shared scaffolding for the tests that have to build real controls.
///
/// Boots the JavaFX toolkit once per JVM. The `:gui` test task runs JavaFX in its
/// own headless platform (`glass.platform=Headless`, see `mindis-gui/build.gradle.kts`),
/// so a display is not needed and a missing toolkit is a real failure rather than a
/// reason to skip: skipping is how 27 of these tests went unnoticed on CI for months.
public final class FxTest {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    /// Why the toolkit could not start, if it could not; reported by every test that
    /// then asks for it, not only the first one to try.
    private static volatile @Nullable Throwable startupFailure;

    private FxTest() {
    }

    /// Runs `body` on the FX thread and waits for it, rethrowing whatever it threw.
    public static void runAndWait(Runnable body) throws InterruptedException {
        if (STARTED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> { });
            } catch (IllegalStateException alreadyRunning) {
                // Toolkit already booted by another test in this JVM; fine.
            } catch (UnsupportedOperationException noToolkit) {
                startupFailure = noToolkit;
            }
        }
        Throwable failure = startupFailure;
        if (failure != null) {
            throw new AssertionError(
                    "No JavaFX platform. The :gui test task sets glass.platform=Headless and "
                            + "prism.order=sw so the toolkit starts without a display - check that "
                            + "those are still set in mindis-gui/build.gradle.kts.", failure);
        }
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }

    /// The first node of the given type below `root`, or throws - a missing
    /// node means the view changed shape and the test's assumptions are stale.
    public static <T extends Node> T find(Node root, Class<T> type) {
        List<T> found = findAll(root, type);
        if (found.isEmpty()) {
            throw new AssertionError("no " + type.getSimpleName() + " in scene graph");
        }
        return found.getFirst();
    }

    /// Every node of the given type below `root`, in scene-graph order.
    ///
    /// Walks the places a control's children actually live, which is not just
    /// [javafx.scene.Parent#getChildrenUnmodifiable()]: a `ScrollPane`'s content and a
    /// `SplitPane`'s items hang off the control rather than its child list, and both
    /// are invisible through the child list until a skin exists - which it does not,
    /// for a graph no `Scene` has laid out yet. A `Labeled`'s graphic is reachable
    /// only through the control too, and in this code base that is where a whole row
    /// can sit (see `AppShell`'s nav entries).
    public static <T extends Node> List<T> findAll(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    /// A [PreferencesService] over `file` instead of the user's real data
    /// directory, reaching the package-private path constructor.
    public static PreferencesService preferencesAt(Path file) {
        return new TestablePreferencesService(file);
    }

    private static <T extends Node> void collect(Node node, Class<T> type, List<T> into) {
        if (type.isInstance(node)) {
            into.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, type, into));
        }
        if (node instanceof Labeled labeled && labeled.getGraphic() != null) {
            collect(labeled.getGraphic(), type, into);
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            collect(scrollPane.getContent(), type, into);
        }
        if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(item -> collect(item, type, into));
        }
    }

    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
