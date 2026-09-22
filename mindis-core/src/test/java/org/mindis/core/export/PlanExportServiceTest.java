package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.model.ArchivedService;
import org.mindis.core.model.CollectionMeta;
import org.mindis.core.model.LiturgicalService;
import org.mindis.core.model.Role;
import org.mindis.core.model.ServiceType;
import org.mindis.core.model.Slot;
import org.mindis.core.persistence.AppDatabase;
import org.mindis.core.persistence.ArchivedServiceRepository;
import org.mindis.core.persistence.RoleRepository;
import org.mindis.core.persistence.ServerRepository;
import org.mindis.core.persistence.ServiceRepository;
import org.mindis.core.persistence.TemplateRepository;
import org.mindis.core.preferences.DataDirectory;

class PlanExportServiceTest {

    @TempDir
    Path tempDir;

    private PlanExportService exportService() {
        return exportService(CollectionMeta.empty());
    }

    /// Repositories are empty: export must handle unknown ids. The data
    /// directory is the temp dir, so a template written into `templates/`
    /// there is picked up as the user's own.
    private PlanExportService exportService(CollectionMeta meta) {
        AppDatabase database = new AppDatabase(new RoleRepository(), new ServerRepository(),
                new TemplateRepository(), new ServiceRepository(), new ArchivedServiceRepository());
        database.updateMeta(meta);
        return new PlanExportService(new ServerRepository(), new RoleRepository(),
                database, new DataDirectory(tempDir));
    }

    private void writeUserTemplate(String content) throws IOException {
        Path templates = tempDir.resolve(PlanExportService.TEMPLATE_DIRECTORY);
        Files.createDirectories(templates);
        Files.writeString(templates.resolve("plan.md.peb"), content);
    }

    /// A 4x3 PNG, the smallest thing that exercises the image paths.
    private static String logoBase64() throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF336699);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return Base64.getEncoder().encodeToString(png.toByteArray());
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
    void pdfRendersNamesOutsideWindows1252() throws IOException {
        // The PDF standard-14 fonts stop at Windows-1252; the bundled DejaVu
        // Sans has to carry names like this one through unchanged.
        ArchivedService archived = new ArchivedService("svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "",
                List.of(new ArchivedService.ArchivedSlot("Acolyte", "s1", "Zoë Šimeček")),
                Instant.now());
        Path target = tempDir.resolve("unicode.pdf");

        exportService().exportArchived(List.of(archived), target, PlanExportFormat.PDF);

        assertTrue(extractText(target).contains("Zoë Šimeček"), "Name was not rendered as itself");
    }

    @Test
    void pdfListsRolesAndServers() throws IOException {
        ArchivedService archived = new ArchivedService("svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "",
                List.of(new ArchivedService.ArchivedSlot("Acolyte", "s1", "Anna Meier")),
                Instant.now());
        Path target = tempDir.resolve("content.pdf");

        exportService().exportArchived(List.of(archived), target, PlanExportFormat.PDF);

        String text = extractText(target);
        assertTrue(text.contains("Acolyte"), "Role missing from PDF");
        assertTrue(text.contains("Anna Meier"), "Server missing from PDF");
        assertTrue(text.contains("St. Mary"), "Service heading missing from PDF");
    }

    private static String extractText(Path pdfFile) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfFile.toFile())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    @Test
    void pdfBoxCanUnmapItsBuffers() {
        // PDFBox unmaps memory-mapped buffers through sun.misc.Unsafe. The
        // dependency is reflective, so nothing but this checks it: without the
        // read edge PDFBox drops to a fallback that java.base refuses, and
        // reports that at SEVERE on the first export.
        ModuleLayer boot = ModuleLayer.boot();
        Optional<Module> pdfboxIo = boot.findModule("org.apache.pdfbox.io");
        assumeTrue(pdfboxIo.isPresent(), "not running on the module path");

        Module unsupported = boot.findModule("jdk.unsupported").orElseThrow();
        assertTrue(pdfboxIo.get().canRead(unsupported),
                "org.apache.pdfbox.io must read jdk.unsupported");
    }

    @Test
    void pdfEmbedsTheParishLogo() throws IOException {
        CollectionMeta meta = CollectionMeta.empty()
                .withDisplayName("St. Mary's Parish")
                .withLogoPngBase64(logoBase64());
        Path target = tempDir.resolve("logo.pdf");

        exportService(meta).exportLive(List.of(service()), target, PlanExportFormat.PDF);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            PDResources resources = pdf.getPage(0).getResources();
            boolean hasImage = false;
            for (COSName name : resources.getXObjectNames()) {
                hasImage |= resources.getXObject(name) instanceof PDImageXObject;
            }
            assertTrue(hasImage, "Parish logo was not embedded in the PDF");
        }
        assertTrue(extractText(target).contains("St. Mary's Parish"), "Parish name missing from PDF");
    }

    @Test
    void rtfEmbedsTheParishLogo() throws IOException {
        CollectionMeta meta = CollectionMeta.empty().withLogoPngBase64(logoBase64());
        Path target = tempDir.resolve("logo.rtf");

        exportService(meta).exportLive(List.of(service()), target, PlanExportFormat.RTF);

        assertTrue(Files.readString(target).contains("\\pngblip"), "Parish logo was not embedded in the RTF");
    }

    @Test
    void exportsWithoutLogoWhenTheCollectionHasNone() throws IOException {
        Path target = tempDir.resolve("plain.rtf");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.RTF);

        assertTrue(Files.size(target) > 0);
        assertTrue(!Files.readString(target).contains("\\pngblip"), "Logo drawn although none is set");
    }

    @Test
    void userTemplateOverridesTheBundledOne() throws IOException {
        writeUserTemplate("# {{title}}\n\nMy own layout\n");
        Path target = tempDir.resolve("custom.txt");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.TXT);

        assertTrue(Files.readString(target).contains("My own layout"), "User template was not used");
    }

    @Test
    void templateDecidesWordingLoopsAndDateFormat() throws IOException {
        // The application hands over values; everything the document says is
        // the template's doing.
        writeUserTemplate("""
                # {{ lang("Altar server plan") }} {{ range.count }}

                {% for service in services %}\
                {{ service.date | date("yyyy-MM-dd") }} {{ service.location | upper }}
                {% for slot in service.slots %}\
                - {% if slot.assigned %}{{ slot.serverName }}{% else %}NOBODY{% endif %}
                {% endfor %}\
                {% endfor %}""");
        Path target = tempDir.resolve("control.txt");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.TXT);

        String content = Files.readString(target);
        assertTrue(content.contains("2026-08-02"), "date filter did not run: " + content);
        assertTrue(content.contains("ST. MARY"), "upper filter did not run: " + content);
        assertTrue(content.contains("NOBODY"), "conditional did not run: " + content);
    }

    @Test
    void templateCanIncludeAnotherFileFromItsOwnDirectory() throws IOException {
        Path templates = tempDir.resolve(PlanExportService.TEMPLATE_DIRECTORY);
        Files.createDirectories(templates);
        Files.writeString(templates.resolve("letterhead.peb"), "Sankt Markus, Musterstadt\n");
        writeUserTemplate("{% include \"letterhead.peb\" %}\n# {{ labels.plan }}\n");
        Path target = tempDir.resolve("include.txt");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.TXT);

        assertTrue(Files.readString(target).contains("Sankt Markus"), "include was not rendered");
    }

    @Test
    void templateCannotIncludeFilesOutsideItsDirectory() throws IOException {
        Files.writeString(tempDir.resolve("secret.txt"), "TOP SECRET");
        writeUserTemplate("{% include \"../secret.txt\" %}\n# {{ labels.plan }}\n");
        Path target = tempDir.resolve("escape.md");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.MARKDOWN);

        String content = Files.readString(target);
        assertTrue(!content.contains("TOP SECRET"), "template read a file outside the template directory");
        assertTrue(content.contains(Role.ACOLYTE), "fallback template did not render the plan");
    }

    @Test
    void brokenUserTemplateFallsBackToTheBundledOne() throws IOException {
        writeUserTemplate("# {{title}}\n\n{{#services}}never closed\n");
        Path target = tempDir.resolve("broken.md");

        exportService().exportLive(List.of(service()), target, PlanExportFormat.MARKDOWN);

        String content = Files.readString(target);
        // The role id itself: an empty role repository has no display name for it.
        assertTrue(content.contains(Role.ACOLYTE), "Fallback template did not render the plan");
        assertTrue(!content.contains("never closed"), "Broken template was used anyway");
    }

    @Test
    void nameWithMarkdownSyntaxSurvivesEveryFormat() throws IOException {
        // A pipe would split a table cell, an asterisk would start emphasis.
        ArchivedService archived = new ArchivedService("svc1", LocalDateTime.of(2026, 8, 2, 10, 0), 60,
                "St. Mary", ServiceType.SUNDAY_MASS, "",
                List.of(new ArchivedService.ArchivedSlot("Acolyte", "s1", "A|B *C*")),
                Instant.now());

        Path text = tempDir.resolve("escaped.txt");
        exportService().exportArchived(List.of(archived), text, PlanExportFormat.TXT);
        assertTrue(Files.readString(text).contains("A|B *C*"), "Name was mangled in TXT");

        Path pdf = tempDir.resolve("escaped.pdf");
        exportService().exportArchived(List.of(archived), pdf, PlanExportFormat.PDF);
        assertTrue(extractText(pdf).contains("A|B *C*"), "Name was mangled in PDF");
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
