package org.mindis.core.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mindis.core.l10n.Localization;

/// Renders the plan as Markdown through the user's template - the one document
/// layout every non-CSV export format is drawn from.
///
/// <p>The template does the composing. It gets values ([PlanTemplateModel]) and
/// Pebble's own control structures - `{% for %}`, `{% if %}`,
/// `{% set %}`, filters like `date(...)` and `numberformat(...)`,
/// macros, `{% include %}` of another file in the template directory -
/// and decides what the document says. The application contributes no wording
/// of its own beyond the translations under `labels` and the
/// `lang("...")` function.
///
/// <p>Two things are deliberately not the template's to choose. Values are
/// Markdown-escaped by default ([MarkdownEscaper]), because a server called
/// `A|B` must not silently break out of a table cell - `{{ value | raw }}`
/// opts out per value. And `{% include %}` resolves inside the template
/// directory only, so a plan export cannot be turned into a way to read
/// arbitrary files.
final class PlanTemplate {

    static final String TEMPLATE_FILE_NAME = "plan.md.peb";
    /// Image destination that every [PlanRenderer] resolves to the
    /// collection's own logo.
    static final String LOGO_DESTINATION = "mindis:logo";
    private static final String MARKDOWN_ESCAPING = "md";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanTemplate.class);

    private final @Nullable Path userTemplate;

    PlanTemplate(@Nullable Path userTemplate) {
        this.userTemplate = userTemplate;
    }

    String render(List<PlanTemplateModel.Service> services, ParishIdentity parish) {
        Map<String, Object> model = PlanTemplateModel.build(services, parish);
        String template = userTemplateText();
        if (template != null) {
            try {
                return evaluate(template, model, userTemplateDirectory());
            } catch (PebbleException | IOException e) {
                LOGGER.warn("Export template {} is broken, using the built-in one instead: {}",
                        userTemplate, e.getMessage());
            }
        }
        try {
            return evaluate(bundledTemplateText(), model, null);
        } catch (PebbleException | IOException e) {
            throw new IllegalStateException("The built-in export template is broken", e);
        }
    }

    private static String evaluate(String template, Map<String, Object> model, @Nullable Path includeRoot)
            throws IOException {
        PebbleEngine.Builder engine = new PebbleEngine.Builder()
                // The template is handed over as text; anything it includes is
                // resolved inside the template directory and nowhere else.
                .loader(new TemplateDirectoryLoader(template, includeRoot))
                .autoEscaping(true)
                .addEscapingStrategy(MARKDOWN_ESCAPING, MarkdownEscaper::escape)
                .defaultEscapingStrategy(MARKDOWN_ESCAPING)
                // An unknown variable renders empty instead of throwing: a
                // template is user content, and half a plan beats no plan.
                .strictVariables(false)
                // Every line the template writes is a line of Markdown, where
                // blank lines separate blocks. Swallowing the newline after a
                // tag would silently glue a heading to the paragraph below it,
                // so the template controls its own line breaks.
                .newLineTrimming(false)
                // Dates and numbers format in the application's language.
                .defaultLocale(Locale.getDefault())
                .extension(new MinDisExtension())
                .cacheActive(false);

        StringWriter rendered = new StringWriter();
        engine.build().getTemplate(TemplateDirectoryLoader.MAIN_TEMPLATE).evaluate(rendered, model);
        return tidy(rendered.toString());
    }

    /// Collapses runs of blank lines and trims the ends. Markdown treats one
    /// blank line and three the same, so nothing about the document changes -
    /// but a template's `{% if %}` lines leave their newlines behind, and the
    /// Markdown export is a file the user hands out, not just an intermediate.
    private static String tidy(String markdown) {
        return markdown.replaceAll("(?:[ \t]*\r?\n){3,}", "\n\n").strip() + "\n";
    }

    private @Nullable Path userTemplateDirectory() {
        return userTemplate == null ? null : userTemplate.getParent();
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

    /// Adds `lang("English text")` so a template can reach any of the
    /// application's translations, not just the ones under `labels`.
    private static final class MinDisExtension extends AbstractExtension {

        @Override
        public Map<String, Function> getFunctions() {
            return Map.of("lang", new LangFunction());
        }
    }

    private static final class LangFunction implements Function {

        @Override
        public List<String> getArgumentNames() {
            return List.of("text");
        }

        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self,
                              EvaluationContext context, int lineNumber) {
            Object text = args.get("text");
            return text == null ? "" : Localization.lang(String.valueOf(text));
        }
    }
}
