package org.mindis.core.export;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import io.pebbletemplates.pebble.error.LoaderException;
import io.pebbletemplates.pebble.loader.Loader;

import org.jspecify.annotations.Nullable;

/// Serves the plan template itself, plus anything it pulls in with
/// `{% include %}` or `{% import %}` - but only from the template
/// directory. A template is a document layout, not a licence to read whatever
/// file the process can reach, so a name that resolves outside that directory
/// (`../../id_rsa`, an absolute path) is refused.
///
/// <p>With no template directory - the built-in template - only the main
/// template resolves and every include fails.
final class TemplateDirectoryLoader implements Loader<String> {

    /// Cache key of the template handed to the engine as text rather than by
    /// name; no file may use it.
    static final String MAIN_TEMPLATE = "<plan>";

    private final String mainTemplate;
    private final @Nullable Path directory;

    TemplateDirectoryLoader(String mainTemplate, @Nullable Path directory) {
        this.mainTemplate = mainTemplate;
        this.directory = directory == null ? null : directory.toAbsolutePath().normalize();
    }

    @Override
    public Reader getReader(String cacheKey) {
        if (MAIN_TEMPLATE.equals(cacheKey)) {
            return Reader.of(mainTemplate);
        }
        Path file = resolve(cacheKey);
        if (file == null) {
            throw new LoaderException(null, "Template not found in the template directory: " + cacheKey);
        }
        try {
            return Reader.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read included template: " + file, e);
        }
    }

    /// The file `name` points at, or null when there is no template directory,
    /// when the name escapes it, or when no such file exists.
    private @Nullable Path resolve(String name) {
        if (directory == null) {
            return null;
        }
        Path file;
        try {
            file = directory.resolve(name).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            return null;
        }
        return file;
    }

    @Override
    public String createCacheKey(String templateName) {
        return templateName;
    }

    @Override
    public boolean resourceExists(String templateName) {
        return MAIN_TEMPLATE.equals(templateName) || resolve(templateName) != null;
    }

    @Override
    public String resolveRelativePath(String relativePath, String anchorPath) {
        // Every include is resolved against the template directory itself, so
        // there is no relative anchoring to do.
        return relativePath;
    }

    @Override
    public void setCharset(String charset) {
        // Templates are read as UTF-8; nothing to configure.
    }

    @Override
    public void setPrefix(String prefix) {
        // The template directory is the prefix.
    }

    @Override
    public void setSuffix(String suffix) {
        // Include names carry their own extension.
    }
}
