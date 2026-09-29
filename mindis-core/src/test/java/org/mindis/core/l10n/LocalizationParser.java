package org.mindis.core.l10n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Finds the localization keys the sources actually use, and compares them against the
/// English bundle - the single source of truth for what a key is (PLAN.md section 2.3).
///
/// Adapted from JabRef's `org.jabref.logic.l10n.LocalizationParser`. mindis builds its UI
/// in code, so there are no FXML files to scan - and therefore no JavaFX toolkit or Mockito
/// needed here. What takes their place is the bundled Pebble export template, which reaches
/// the same bundle through its own `lang("...")` function.
final class LocalizationParser {

    /// Directories of the modules whose Java sources may use localization keys. Resolved
    /// relative to `mindis-core`, the working directory of the `:core` test task.
    private static final List<String> MODULE_DIRECTORIES = List.of("mindis-core", "mindis-gui");

    /// `lang("English text")` as the Pebble export template writes it. Pebble's own syntax
    /// is simple enough that a regex is honest here, unlike Java source.
    private static final Pattern TEMPLATE_LANG_PATTERN =
            Pattern.compile("lang\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /// Pebble comments, `{# ... #}` - the bundled template documents its own `lang(...)`
    /// function inside one, which is no more a usage than a Java comment would be.
    private static final Pattern TEMPLATE_COMMENT_PATTERN = Pattern.compile("\\{#.*?#}", Pattern.DOTALL);

    private LocalizationParser() {
    }

    /// Returns all keys used in the sources which are not declared in the English bundle.
    static SortedSet<LocalizationEntry> findMissingKeys() throws IOException {
        Set<String> englishKeys = getEnglishKeys();
        return findLocalizationEntries().stream()
                .filter(entry -> !englishKeys.contains(entry.getKey()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /// Returns all keys declared in the English bundle which are not used in the sources.
    static SortedSet<String> findObsolete() throws IOException {
        Set<String> keysInSourceFiles = findLocalizationEntries().stream()
                .map(LocalizationEntry::getKey)
                .collect(Collectors.toSet());
        return getEnglishKeys().stream()
                .filter(key -> !keysInSourceFiles.contains(key))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /// Returns the raw argument list of every `Localization.lang(...)` call, so that a test
    /// can check how the call was written and not just which key came out of it.
    static Set<LocalizationEntry> findLocalizationParametersStringsInJavaFiles() throws IOException {
        return findInModules("src/main/java", ".java",
                path -> parseJavaFile(path, JavaLocalizationEntryParser::getLocalizationParameter));
    }

    private static Set<LocalizationEntry> findLocalizationEntries() throws IOException {
        Set<LocalizationEntry> entries = new HashSet<>();
        entries.addAll(findInModules("src/main/java", ".java",
                path -> parseJavaFile(path, JavaLocalizationEntryParser::getLanguageKeysInString)));
        entries.addAll(findInModules("src/main/resources", ".peb",
                LocalizationParser::getLanguageKeysInTemplateFile));
        return entries;
    }

    /// The bundled export template calls `lang("...")` for any wording that is not already
    /// in its `labels` map, so those keys are used just as much as the Java ones.
    private static List<LocalizationEntry> getLanguageKeysInTemplateFile(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        content = TEMPLATE_COMMENT_PATTERN.matcher(content).replaceAll("");

        List<LocalizationEntry> result = new ArrayList<>();
        Matcher matcher = TEMPLATE_LANG_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(new LocalizationEntry(path, LocalizationKey.fromEscapedJavaString(matcher.group(1)).getKey()));
        }
        return result;
    }

    /// Collects the localization entries of all matching files below the given source
    /// directory of each module.
    ///
    /// @param sourceDirectory the module relative directory to walk, e.g. `src/main/java`
    /// @param extension       only files having this file name extension are parsed
    /// @param extractor       extracts the localization entries of a single file
    private static Set<LocalizationEntry> findInModules(String sourceDirectory,
                                                        String extension,
                                                        Function<Path, Collection<LocalizationEntry>> extractor)
            throws IOException {
        Set<LocalizationEntry> result = new HashSet<>();
        for (String module : MODULE_DIRECTORIES) {
            Path root = Path.of("..", module).resolve(sourceDirectory).normalize();
            if (!Files.isDirectory(root)) {
                continue;
            }
            // Files.walk holds a directory handle, thus the stream needs to be closed
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(extension))
                     .map(extractor)
                     .forEach(result::addAll);
            }
        }
        return result;
    }

    /// Returns the trimmed key set of the English bundle. Each key is already unescaped by
    /// [Properties], so it is re-escaped here to be comparable to what the parser reads out
    /// of a Java string literal.
    static SortedSet<String> getEnglishKeys() {
        return getProperties(LocalizationFiles.ENGLISH_BUNDLE_RESOURCE).keySet().stream()
                .map(Object::toString)
                .map(String::trim)
                .map(key -> key
                        .replace("\\", "\\\\")
                        .replace("\n", "\\n"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /// The Java source files, relative to their module's source root, that mention `token`.
    /// Comments are blanked out first, so a method named in a doc comment does not count as
    /// a use of it.
    static SortedSet<String> findFilesUsing(String token) throws IOException {
        SortedSet<String> result = new TreeSet<>();
        for (String module : MODULE_DIRECTORIES) {
            Path root = Path.of("..", module).resolve("src/main/java").normalize();
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String content = JavaLocalizationEntryParser.blankOutComments(
                            Files.readString(path, StandardCharsets.UTF_8));
                    if (content.contains(token)) {
                        result.add(root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"));
                    }
                }
            }
        }
        return result;
    }

    static Properties getProperties(String resourcePath) {
        InputStream inputStream = LocalizationParser.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("Could not find the properties file " + resourcePath);
        }
        Properties properties = new Properties();
        try (inputStream; InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /// Reads the given Java file and turns everything the parser finds in it into
    /// localization entries.
    ///
    /// The line endings are normalized to `\n`, because the parser treats `\n` as the end
    /// of a line comment.
    private static List<LocalizationEntry> parseJavaFile(Path path, Function<String, List<String>> parser) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return parser.apply(String.join("\n", lines)).stream()
                     .map(key -> new LocalizationEntry(path, key))
                     .toList();
    }
}
