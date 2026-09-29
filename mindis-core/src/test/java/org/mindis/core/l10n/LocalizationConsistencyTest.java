package org.mindis.core.l10n;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.mindis.core.preferences.AppLanguage;

/// Guards the full-text localization convention (PLAN.md section 2.3): every key used in
/// the sources exists in the English bundle, the English bundle contains nothing else, and
/// the translations stay aligned with it.
///
/// Adapted from JabRef's `org.jabref.logic.l10n.LocalizationConsistencyTest`. mindis builds
/// its UI in code rather than from FXML, so there is nothing here that needs a JavaFX
/// toolkit - unlike JabRef's version, this test runs headless.
class LocalizationConsistencyTest {

    private static AppLanguage[] installedLanguages() {
        return AppLanguage.values();
    }

    /// The bundles on disk, the locales [Localization] will load, and the languages
    /// [AppLanguage] offers the user must be the same set - a file nobody can select is as
    /// broken as a language with no file.
    @Test
    void allFilesMustBeInLanguages() throws IOException {
        Pattern propertiesFile = Pattern.compile("%s_(.+)\\.properties".formatted(LocalizationFiles.BUNDLE_NAME));
        Set<String> localizationFiles = new HashSet<>();
        try (DirectoryStream<Path> directoryStream =
                     Files.newDirectoryStream(LocalizationFiles.RESOURCE_SOURCE_DIRECTORY)) {
            for (Path fullPath : directoryStream) {
                var matcher = propertiesFile.matcher(fullPath.getFileName().toString());
                if (matcher.matches()) {
                    localizationFiles.add(matcher.group(1));
                }
            }
        }

        Set<String> offeredLanguages = Stream.of(AppLanguage.values())
                                             .map(AppLanguage::tag)
                                             .collect(Collectors.toSet());
        Set<String> loadableLocales = Localization.supportedLocales().stream()
                                                  .map(Locale::getLanguage)
                                                  .collect(Collectors.toSet());
        assertAll(
                () -> assertEquals(offeredLanguages, localizationFiles,
                        "A bundle exists that AppLanguage does not offer, or vice versa"),
                () -> assertEquals(offeredLanguages, loadableLocales,
                        "AppLanguage offers a language that Localization.supportedLocales() will not load, "
                                + "or vice versa"));
    }

    /// There is no unsuffixed base bundle: English is a language like any other, in
    /// `MinDis_en.properties`. [Localization] resolves an unsupported locale to English
    /// instead, so nothing needs a second copy of the English text.
    @Test
    void thereIsNoBaseBundle() {
        assertFalse(Files.exists(LocalizationFiles.RESOURCE_SOURCE_DIRECTORY
                        .resolve(LocalizationFiles.BUNDLE_NAME + ".properties")),
                "Delete it - keeping a base bundle in sync with the English one by hand is exactly "
                        + "the drift this test exists to prevent.");
    }

    @Test
    void anyLocaleResolvesToASupportedOne() {
        assertAll(
                () -> assertEquals(Locale.ENGLISH, Localization.resolveSupportedLocale(Locale.FRENCH)),
                () -> assertEquals(Locale.GERMAN, Localization.resolveSupportedLocale(Locale.GERMANY)),
                () -> assertEquals(Locale.ENGLISH, Localization.resolveSupportedLocale(Locale.US)));
    }

    @ParameterizedTest
    @MethodSource("installedLanguages")
    void ensureNoDuplicates(AppLanguage language) {
        String resource = LocalizationFiles.resource(language.tag());

        DuplicationDetectionProperties properties = new DuplicationDetectionProperties();
        try (InputStream inputStream = LocalizationConsistencyTest.class.getResourceAsStream(resource)) {
            assertNotNull(inputStream, "Missing bundle " + resource);
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(List.of(), properties.getDuplicates(), "Duplicate keys inside bundle " + resource);
    }

    /// In a full-text bundle the English "translation" is the key itself, so a mismatch here
    /// means someone edited the English text on one side only.
    @Test
    void keyValueShouldBeEqualForEnglishPropertiesMessages() {
        Properties englishKeys = LocalizationParser.getProperties(LocalizationFiles.ENGLISH_BUNDLE_RESOURCE);
        for (Map.Entry<Object, Object> entry : englishKeys.entrySet()) {
            String expectedKeyEqualsKey = "%s=%s".formatted(entry.getKey(), entry.getKey().toString().replace("\n", "\\n"));
            String actualKeyEqualsValue = "%s=%s".formatted(entry.getKey(), entry.getValue().toString().replace("\n", "\\n"));
            assertEquals(expectedKeyEqualsKey, actualKeyEqualsValue);
        }
    }

    /// A translation for a key that no longer exists is dead weight, and usually the leftover
    /// of an English text that was reworded without the translation following.
    @ParameterizedTest
    @MethodSource("installedLanguages")
    void translationsContainNoUnknownKeys(AppLanguage language) {
        Set<String> englishKeys = LocalizationParser.getProperties(LocalizationFiles.ENGLISH_BUNDLE_RESOURCE)
                                                    .stringPropertyNames();
        Set<String> unknownKeys = LocalizationParser.getProperties(LocalizationFiles.resource(language.tag()))
                                                    .stringPropertyNames().stream()
                                                    .filter(key -> !englishKeys.contains(key))
                                                    .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(), unknownKeys,
                unknownKeys.stream().collect(Collectors.joining("\n",
                        """

                                KEYS IN THE %s BUNDLE THAT THE ENGLISH BUNDLE DOES NOT HAVE.
                                Either the English text was reworded without the translation following, or the key is obsolete.

                                """.formatted(language.tag()),
                        "\n")));
    }

    @Test
    void languageKeysShouldNotContainUnderscoresForSpaces() throws IOException {
        List<LocalizationEntry> quotedEntries = LocalizationParser
                .findLocalizationParametersStringsInJavaFiles()
                .stream()
                .filter(key -> key.getKey().contains("\\_"))
                .toList();
        assertEquals(List.of(), quotedEntries,
                """
                        Language keys must not use underscores for spaces! Use "This is a message" instead of "This_is_a_message".
                        Please correct the following entries:
                        """
                        + quotedEntries.stream()
                                       .map(key -> "\n%s (%s)\n".formatted(key.getKey(), key.getPath()))
                                       .toList());
    }

    @Test
    void languageKeysShouldNotContainHtmlBrAndHtmlP() throws IOException {
        List<LocalizationEntry> entriesWithHtml = LocalizationParser
                .findLocalizationParametersStringsInJavaFiles()
                .stream()
                .filter(key -> key.getKey().contains("<br>") || key.getKey().contains("<p>"))
                .toList();
        assertEquals(List.of(), entriesWithHtml,
                """
                        Language keys must not contain HTML <br> or <p>. Use \\n for a line break.
                        Please correct the following entries:
                        """
                        + entriesWithHtml.stream()
                                         .map(key -> "\n%s (%s)\n".formatted(key.getKey(), key.getPath()))
                                         .toList());
    }

    @Test
    void findMissingLocalizationKeys() throws IOException {
        List<LocalizationEntry> missingKeys = new ArrayList<>(LocalizationParser.findMissingKeys());
        assertEquals(List.of(), missingKeys,
                missingKeys.stream()
                           .map(key -> LocalizationKey.fromKey(key.getKey()))
                           .map(key -> "%s=%s".formatted(
                                   key.getEscapedPropertiesKey(),
                                   key.getValueForEnglishPropertiesFile()))
                           .collect(Collectors.joining("\n",
                                   """

                                           DETECTED LANGUAGE KEYS WHICH ARE NOT IN THE ENGLISH LANGUAGE FILE.
                                           PASTE THESE INTO THE ENGLISH LANGUAGE FILE "MinDis_en.properties".
                                           Search for a proper place; typically related keys are grouped together.
                                           If a similar key is already present, please adapt your wording instead of
                                           adding load to translators by adding a new key.

                                           """,
                                   "\n\n")));
    }

    @Test
    void findObsoleteLocalizationKeys() throws IOException {
        Set<String> obsoleteKeys = LocalizationParser.findObsolete();
        assertEquals(Set.of(), obsoleteKeys,
                obsoleteKeys.stream().collect(Collectors.joining("\n",
                        "Obsolete keys found in the English language file: \n\n",
                        """


                                1. CHECK IF THE KEY IS REALLY NOT USED ANYMORE.
                                2. REMOVE THESE FROM ALL "MinDis_*.properties" FILES.

                                """)));
    }

    /// mindis's localization infrastructure only supports a plain string literal as the
    /// key: a key held in a variable is invisible to every check in this class and would
    /// silently never be translated. Somewhere to store a key and translate it later is not
    /// needed - a `switch` in the enum's own `displayName()` keeps the lookup lazy while
    /// leaving the literal where the parser can see it (see `EnumDisplay`,
    /// `ConstraintDisplay`, `WidgetType#title()`).
    @Test
    void localizationParameterMustIncludeAString() throws IOException {
        Set<LocalizationEntry> keys = LocalizationParser.findLocalizationParametersStringsInJavaFiles();
        for (LocalizationEntry entry : keys) {
            assertTrue(entry.getKey().startsWith("\""),
                    "Illegal localization parameter found. Must include a String with potential concatenation or "
                            + "replacement parameters. Illegal parameter: Localization.lang(" + entry.getKey()
                            + ") in " + entry.getPath());
        }
    }

    /// The one text that genuinely cannot be a literal is the export template's
    /// `lang(...)` argument, which comes out of a `.peb` file the user may have edited.
    /// [Localization#translateDynamic] serves that one case; anything else reaching for it
    /// is a key that should have been written out instead.
    @Test
    void onlyTheTemplateBridgeTranslatesDynamically() throws IOException {
        assertEquals(Set.of("org/mindis/core/export/PlanTemplate.java"),
                LocalizationParser.findFilesUsing("Localization.translateDynamic("),
                "Localization.translateDynamic is the export template's escape hatch. Write the key as a "
                        + "literal inside Localization.lang(...) instead.");
    }

    @ParameterizedTest
    @MethodSource("installedLanguages")
    void resourceBundleExists(AppLanguage language) {
        assertTrue(Files.exists(LocalizationFiles.RESOURCE_SOURCE_DIRECTORY
                .resolve("%s_%s.properties".formatted(LocalizationFiles.BUNDLE_NAME, language.tag()))));
    }

    @ParameterizedTest
    @MethodSource("installedLanguages")
    void languageCanBeLoaded(AppLanguage language) {
        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(language.locale());
            ResourceBundle messages = ResourceBundle.getBundle(
                    "org.mindis.core.l10n." + LocalizationFiles.BUNDLE_NAME, language.locale());
            assertNotNull(messages);
        } finally {
            Locale.setDefault(oldLocale);
        }
    }

    private static class DuplicationDetectionProperties extends Properties {

        @Serial private static final long serialVersionUID = 1L;

        private final List<String> duplicates = new ArrayList<>();

        /// Overrides the [java.util.Hashtable] `put` so duplicates can be detected - loading
        /// silently keeps the last value otherwise.
        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
                duplicates.add(String.valueOf(key));
            }
            return super.put(key, value);
        }

        List<String> getDuplicates() {
            return duplicates;
        }
    }
}
