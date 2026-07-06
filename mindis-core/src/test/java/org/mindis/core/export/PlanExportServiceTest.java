package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.model.Role;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.planning.AcceptedPlan;

class PlanExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsPdfFile() throws IOException {
        // Repositories on empty temp files: export must handle unknown ids.
        PlanExportService exportService = new PlanExportService(
                new ServerRepositoryStub(tempDir), new ServiceRepositoryStub(tempDir));
        AcceptedPlan plan = new AcceptedPlan(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                List.of(new AcceptedPlan.PlannedAssignment("a1", "svc1", Role.ACOLYTE, null, false)));
        Path target = tempDir.resolve("plan.pdf");

        exportService.exportPdf(plan, target);

        assertTrue(Files.exists(target));
        assertTrue(Files.size(target) > 500, "PDF suspiciously small");
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(target)) {
            assertTrue(in.read(head) == 4);
        }
        assertTrue(new String(head).startsWith("%PDF"), "Not a PDF file");
    }

    private static final class ServerRepositoryStub extends ServerRepository {
        ServerRepositoryStub(Path dir) {
            super(dir.resolve("servers.json"));
        }
    }

    private static final class ServiceRepositoryStub extends ServiceRepository {
        ServiceRepositoryStub(Path dir) {
            super(dir.resolve("services.json"));
        }
    }
}
