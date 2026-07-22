package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;

class PlanExportServiceTest {

    @TempDir
    Path tempDir;

    private PlanExportService exportService() {
        // Repositories on empty temp files: export must handle unknown ids.
        return new PlanExportService(new ServerRepository(), new RoleRepository());
    }

    private static LiturgicalService service() {
        return new LiturgicalService("svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60, "St. Mary",
                ServiceType.SUNDAY_MASS, List.of(new Slot("slot-0", Role.ACOLYTE, null, false)), "");
    }

    @Test
    void exportsPdfFileFromLiveServices() throws IOException {
        Path target = tempDir.resolve("plan.pdf");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.PDF);

        assertTrue(Files.exists(target));
        assertTrue(Files.size(target) > 500, "PDF suspiciously small");
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(target)) {
            assertEquals(4, in.read(head));
        }
        assertTrue(new String(head).startsWith("%PDF"), "Not a PDF file");
    }

    @Test
    void exportsEveryTextFormatFromLiveServices() throws IOException {
        PlanExportService exportService = exportService();
        for (PlanExportFormat format : List.of(
                PlanExportFormat.CSV, PlanExportFormat.TXT, PlanExportFormat.RTF, PlanExportFormat.MARKDOWN)) {
            Path target = tempDir.resolve("plan." + format.extension());
            exportService.exportLive(List.of(service()), target, format);
            assertTrue(Files.exists(target), format + " file was not written");
            assertTrue(Files.size(target) > 0, format + " file is empty");
        }
    }

    @Test
    void exportsArchivedSnapshotUsingItsOwnNames() throws IOException {
        // No server or role exists in the (empty) repositories, yet the frozen
        // snapshot still renders the captured display names.
        ArchivedService archived = new ArchivedService("svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "",
                List.of(new ArchivedService.ArchivedSlot("Acolyte", "gone", "Deleted Server")),
                Instant.now());
        Path target = tempDir.resolve("archived.md");

        exportService().exportArchived(List.of(archived), target, PlanExportFormat.MARKDOWN);

        String content = Files.readString(target);
        assertTrue(content.contains("Deleted Server"), "Archived server name missing from export");
        assertTrue(content.contains("Acolyte"), "Archived role name missing from export");
    }
}
