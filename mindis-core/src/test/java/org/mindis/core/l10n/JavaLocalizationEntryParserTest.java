package org.mindis.core.l10n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Adapted from JabRef's `org.jabref.logic.l10n.JavaLocalizationEntryParserTest`.
class JavaLocalizationEntryParserTest {

    static Stream<Arguments> singleLineChecks() {
        return Stream.of(
                Arguments.of("Localization.lang(\"one per line\")", "one per line"),

                // '\c' is an escaped character, so a backslash in a key is written "\\" in
                // the Java source and stays a double backslash in the .properties file.
                Arguments.of("Localization.lang(\"Path C\\\\:\\\\temp\")", "Path C\\\\:\\\\temp"),

                // " is kept unescaped
                Arguments.of("Localization.lang(\"\\\"Hey\\\"\")", "\"Hey\""),

                // \n is a "real" newline character in the simulated Java source code
                Arguments.of("Localization.lang(\"multi \" + \n\"line\")", "multi line"),
                Arguments.of("Localization.lang(\n            \"A string\")", "A string"),

                Arguments.of("Localization.lang(\"one per line with var\", var)", "one per line with var"),
                Arguments.of("Localization.lang(\"%0 of %1 slots assigned\", assigned, total)", "%0 of %1 slots assigned"),
                Arguments.of("Localization.lang(\"Archive up to... (or 'all')\")", "Archive up to... (or 'all')"),
                Arguments.of("Localization.lang(\"Really delete the plan '%0'?\")", "Really delete the plan '%0'?"),
                Arguments.of("Localization.lang(\"Export failed, e.g. \\\"plan.pdf\\\"\");",
                        "Export failed, e.g. \"plan.pdf\""),

                // \n is allowed: it appears as the two characters "\n" both in the source and
                // as the key in the .properties file, so it is written \\n here.
                // See also https://stackoverflow.com/a/10285687/873282
                Arguments.of("Localization.lang(\"First line\\nSecond line\")", "First line\\nSecond line")
        );
    }

    static Stream<Arguments> multiLineChecks() {
        return Stream.of(
                Arguments.of("Localization.lang(\"two per line\") Localization.lang(\"two per line\")",
                        List.of("two per line", "two per line"))
        );
    }

    static Stream<Arguments> singleLineParameterChecks() {
        return Stream.of(
                Arguments.of("Localization.lang(\"one per line\")", "\"one per line\""),
                Arguments.of("Localization.lang(\"one per line\" + var)", "\"one per line\" + var"),
                Arguments.of("Localization.lang(var + \"one per line\")", "var + \"one per line\""),
                Arguments.of("Localization.lang(\"Version %0\", version)", "\"Version %0\", version")
        );
    }

    static Stream<String> causesIllegalState() {
        return Stream.of(
                "Localization.lang(\"Ends with a space \")",
                // "\\n" in the *.java source file
                "Localization.lang(\"Escaped newline\\\\nthere\")"
        );
    }

    /// Code snippets in which `Localization.lang` only occurs inside a comment, hence no key
    /// must be found. mindis writes its doc comments as Markdown (`///`), so those count too.
    static Stream<String> commentedOutLocalizations() {
        return Stream.of(
                "// Localization.lang(\"key in line comment\")",
                "/// Localization.lang(\"key in markdown comment\")",
                "int i = 0; // Localization.lang(\"key in trailing line comment\")",
                "/* Localization.lang(\"key in block comment\") */",
                "/*\n * Localization.lang(\"key in multi line block comment\")\n */",
                "/" + "*" + "*" + "\n * Localization.lang(\"key in javadoc\")\n */",
                // unterminated block comment at the end of a file
                "/* Localization.lang(\"key in unterminated block comment\")",
                // the export template documents its own lang(...) inside a comment
                "/// Functions: lang(\"English text\") for any other translation."
        );
    }

    static Stream<Arguments> commentsMixedWithCode() {
        return Stream.of(
                // the comment must not swallow the real key
                Arguments.of("// Localization.lang(\"commented\")\nLocalization.lang(\"real\")", List.of("real")),
                Arguments.of("Localization.lang(\"real\") // Localization.lang(\"commented\")", List.of("real")),
                Arguments.of("/* Localization.lang(\"commented\") */ Localization.lang(\"real\")", List.of("real")),
                // a comment inside the argument list is ignored, the key is still found
                Arguments.of("Localization.lang(\"real\" /* comment */)", List.of("real")),
                Arguments.of("Localization.lang(\"real\" // comment\n)", List.of("real")),
                // "//" inside a string literal does not start a comment
                Arguments.of("String url = \"https://example.org\"; Localization.lang(\"real\")", List.of("real")),
                // a quote inside a comment must not be treated as the start of a string literal
                Arguments.of("// it's commented\nLocalization.lang(\"real\")", List.of("real")),
                Arguments.of("/* \" */ Localization.lang(\"real\")", List.of("real")),
                // character literals containing quotes do not confuse the parser
                Arguments.of("char c = '\"'; Localization.lang(\"real\")", List.of("real")),
                // comment markers inside a text block do not start a comment
                Arguments.of("String s = \"\"\"\n// no comment /* here\n\"\"\"; Localization.lang(\"real\")", List.of("real"))
        );
    }

    @ParameterizedTest
    @MethodSource("commentedOutLocalizations")
    void localizationInCommentIsIgnored(String code) {
        assertEquals(List.of(), JavaLocalizationEntryParser.getLanguageKeysInString(code));
    }

    @ParameterizedTest
    @MethodSource("commentsMixedWithCode")
    void localizationKeysAreFoundNextToComments(String code, List<String> expectedLanguageKeys) {
        assertEquals(expectedLanguageKeys, JavaLocalizationEntryParser.getLanguageKeysInString(code));
    }

    @Test
    void blankOutCommentsKeepsLengthAndLineStructure() {
        String source = "int i = 0; // comment\n/* block\ncomment */ int j = 1;";
        String blanked = JavaLocalizationEntryParser.blankOutComments(source);
        assertEquals(source.length(), blanked.length());
        assertEquals("int i = 0;" + " ".repeat(11) + "\n" + " ".repeat(8) + "\n" + " ".repeat(10) + " int j = 1;",
                blanked);
    }

    @ParameterizedTest
    @MethodSource("singleLineChecks")
    void localizationKeyParsing(String code, String expectedLanguageKey) {
        assertEquals(List.of(expectedLanguageKey), JavaLocalizationEntryParser.getLanguageKeysInString(code));
    }

    @ParameterizedTest
    @MethodSource("multiLineChecks")
    void severalLocalizationKeysPerSnippet(String code, List<String> expectedLanguageKeys) {
        assertEquals(expectedLanguageKeys, JavaLocalizationEntryParser.getLanguageKeysInString(code));
    }

    @ParameterizedTest
    @MethodSource("singleLineParameterChecks")
    void localizationParameterParsing(String code, String expectedParameter) {
        assertEquals(List.of(expectedParameter), JavaLocalizationEntryParser.getLocalizationParameter(code));
    }

    @ParameterizedTest
    @MethodSource("causesIllegalState")
    void throwsOnIllegalKey(String code) {
        assertThrows(IllegalStateException.class, () -> JavaLocalizationEntryParser.getLanguageKeysInString(code));
    }
}
