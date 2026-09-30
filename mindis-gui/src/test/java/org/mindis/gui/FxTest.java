package org.mindis.gui;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;

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
        T found = findOrNull(root, type);
        if (found == null) {
            throw new AssertionError("no " + type.getSimpleName() + " in scene graph");
        }
        return found;
    }

    /// A [PreferencesService] over `file` instead of the user's real data
    /// directory, reaching the package-private path constructor.
    public static PreferencesService preferencesAt(Path file) {
        return new TestablePreferencesService(file);
    }

    private static <T extends Node> @Nullable T findOrNull(Node root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildrenUnmodifiable()) {
                T found = findOrNull(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        if (root instanceof ScrollPane scrollPane) {
            return findOrNull(scrollPane.getContent(), type);
        }
        if (root instanceof SplitPane splitPane) {
            for (Node child : splitPane.getItems()) {
                T found = findOrNull(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class TestablePreferencesService extends PreferencesService {
        TestablePreferencesService(Path file) {
            super(file);
        }
    }
}
