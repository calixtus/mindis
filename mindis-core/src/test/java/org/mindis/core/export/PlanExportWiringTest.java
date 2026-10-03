package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.avaje.inject.BeanScope;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.mindis.core.preferences.DataDirectory;

/// Formats reach [PlanExportService] only as beans, so a format whose strategy is
/// missing its `@Singleton` would compile and fail only when a user picks it.
class PlanExportWiringTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(PlanExportFormat.class)
    void containerWiresAStrategyForEveryFormat(PlanExportFormat format) {
        try (BeanScope scope = BeanScope.builder()
                .bean(DataDirectory.class, new DataDirectory(tempDir))
                .build()) {
            Path target = tempDir.resolve("plan." + format.extension());
            scope.get(PlanExportService.class).exportLive(List.of(), target, format);
            assertTrue(Files.exists(target));
        }
    }
}
