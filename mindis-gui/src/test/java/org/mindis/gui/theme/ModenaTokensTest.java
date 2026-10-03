package org.mindis.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import atlantafx.base.theme.NordDark;
import com.dlsc.gemsfx.SearchField;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.Test;

import org.mindis.gui.FxTest;
import org.mindis.gui.util.SearchFields;

/// GemsFX's popups run their first style pass inside their own `show()`, before the window
/// is registered and so before `ThemeManager` adds [ThemeStyler#stylesheet] to it. That
/// pass sees the user-agent stylesheet only, so the Modena tokens GemsFX looks up have to
/// be there: the search popup is shown here with nothing but that layer installed.
class ModenaTokensTest {

    @Test
    void aSearchPopupResolvesEveryLookupWithTheUserAgentLayerAlone() throws Exception {
        List<String> cssWarnings = new CopyOnWriteArrayList<>();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    cssWarnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger cssLog = Logger.getLogger("javafx.css");
        cssLog.addHandler(collector);
        Stage[] stage = new Stage[1];
        @SuppressWarnings("unchecked")
        SearchField<String>[] field = new SearchField[1];
        try {
            FxTest.runAndWait(() -> {
                Application.setUserAgentStylesheet(ThemeStyler.withModenaTokens(new NordDark()).getUserAgentStylesheet());
                field[0] = new SearchField<>();
                SearchFields.applyTheme(field[0]);
                field[0].setSuggestionProvider(request -> List.of("Becker", "Bauer", "Braun"));
                stage[0] = new Stage();
                stage[0].setScene(new Scene(new StackPane(field[0]), 400, 300));
                stage[0].show();
                field[0].getEditor().requestFocus();
                field[0].getEditor().setText("B");
            });
            // SearchField runs the suggestion lookup on a background service and shows the
            // popup when it returns.
            long deadline = System.currentTimeMillis() + 5_000;
            int[] windows = {0};
            while (windows[0] < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                FxTest.runAndWait(() -> windows[0] = Window.getWindows().size());
            }

            assertEquals(2, windows[0], "the suggestion popup never opened");
            assertTrue(cssWarnings.isEmpty(), String.join("\n", cssWarnings));
        } finally {
            cssLog.removeHandler(collector);
            FxTest.runAndWait(() -> {
                if (stage[0] != null) {
                    stage[0].close();
                }
                Application.setUserAgentStylesheet(null);
            });
        }
    }
}
