package org.mindis.core.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Renders a [PlanExportDocument] into Markdown through a Mustache template -
/// the one document layout every non-CSV export format is drawn from.
///
/// <p>The template is the user's if `templates/plan.md.mustache` exists in the
/// data directory, otherwise the one bundled next to this class. A user
/// template that fails to compile or render is reported and the bundled one is
/// used, so a typo in a template never costs the user their export.
///
/// <p>Values are Markdown-escaped on the way in ([#escapeMarkdown]), so a
/// server called `A|B` cannot break out of a table cell.
final class PlanTemplate {

    static final String TEMPLATE_FILE_NAME = "plan.md.mustache";
    /// Image destination that every [PlanRenderer] resolves to the
    /// collection's own logo.
    static final String LOGO_DESTINATION = "mindis:logo";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTemplate.class);

    private final @Nullable Path userTemplate;

    PlanTemplate(@Nullable Path userTemplate) {
        this.userTemplate = userTemplate;
    }

    String render(PlanExportDocument document, ParishIdentity parish) {
        Map<String, Object> model = buildModel(document, parish);
        String template = userTemplateText();
        if (template != null) {
            try {
                return compile().compile(template).execute(model);
            } catch (MustacheException e) {
                LOGGER.warn("Export template {} is broken, using the built-in one instead: {}",
                        userTemplate, e.getMessage());
            }
        }
        return compile().compile(bundledTemplateText()).execute(model);
    }

    private Mustache.Compiler compile() {
        return Mustache.compiler()
                .escapeHTML(false)
                .withEscaper(PlanTemplate::escapeMarkdown)
                // A mistyped key renders empty instead of throwing: a template
                // is user content, and half a plan beats no plan.
                .defaultValue("");
    }

    private @Nullable String userTemplateText() {
        if (userTemplate == null || !Files.isRegularFile(userTemplate)) {
            return null;
        }
        try {
            return Files.readString(userTemplate, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not read export template {}, using the built-in one instead", userTemplate, e);
            return null;
        }
    }

    private static String bundledTemplateText() {
        try (InputStream in = PlanTemplate.class.getResourceAsStream(TEMPLATE_FILE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Bundled export template is missing: " + TEMPLATE_FILE_NAME);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the bundled export template", e);
        }
    }

    /// The data a template can reach: plain maps and lists rather than the
    /// records themselves, so the template contract does not move when internal
    /// types do, and no module has to be opened for reflection.
    private static Map<String, Object> buildModel(PlanExportDocument document, ParishIdentity parish) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("service", document.headers().service());
        headers.put("role", document.headers().role());
        headers.put("server", document.headers().server());
        headers.put("count", document.headers().count());

        List<Map<String, Object>> services = new ArrayList<>();
        for (PlanExportDocument.ServiceSection section : document.services()) {
            List<Map<String, Object>> assignments = new ArrayList<>();
            for (PlanExportDocument.AssignmentRow row : section.assignments()) {
                assignments.add(Map.of("role", row.role(), "serverName", row.serverName()));
            }
            services.add(Map.of("heading", section.heading(), "assignments", assignments));
        }

        List<Map<String, Object>> summary = new ArrayList<>();
        for (PlanExportDocument.SummaryRow row : document.summary()) {
            summary.add(Map.of("serverName", row.serverName(), "count", row.count()));
        }

        Map<String, Object> parishModel = new LinkedHashMap<>();
        parishModel.put("name", parish.name());
        parishModel.put("hasName", !parish.name().isEmpty());
        parishModel.put("hasLogo", parish.logoPng() != null);
        parishModel.put("logo", LOGO_DESTINATION);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("title", document.title());
        model.put("subtitle", document.subtitle());
        model.put("headers", headers);
        model.put("services", services);
        model.put("hasServices", !document.services().isEmpty());
        model.put("summaryHeading", document.summaryHeading());
        model.put("summary", summary);
        model.put("parish", parishModel);
        return model;
    }

    /// Escapes the Markdown characters that change meaning wherever they
    /// appear: emphasis, code, links, angle brackets, and the pipe that would
    /// split a table cell in two.
    ///
    /// <p>Block markers (`#`, `-`, `+`, `>`) are deliberately left alone. They
    /// only mean anything at the start of a line, which is a position the
    /// template author controls and a value almost never lands in; escaping
    /// them defensively would print `\-` for every open slot and `2\. 8\. 2026`
    /// for every date in the Markdown export itself.
    static String escapeMarkdown(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ("\\`*_[]<>|".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
