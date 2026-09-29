package org.mindis.core.l10n;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extracts the arguments of every `Localization.lang(...)` call in a Java source file.
///
/// Adapted from JabRef's `org.jabref.logic.l10n.JavaLocalizationEntryParser`.
final class JavaLocalizationEntryParser {

    private enum CommentState {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private static final String INFINITE_WHITESPACE = "\\s*";
    private static final String DOT = "\\.";
    private static final Pattern LOCALIZATION_START_PATTERN = Pattern.compile(
            "Localization" + INFINITE_WHITESPACE + DOT + INFINITE_WHITESPACE + "lang"
                    + INFINITE_WHITESPACE + "\\(");

    private static final Pattern ESCAPED_QUOTATION_SYMBOL = Pattern.compile("\\\\\"");

    private static final String QUOTATION_PLACEHOLDER = "QUOTATIONPLACEHOLDER";
    private static final Pattern QUOTATION_SYMBOL_PATTERN = Pattern.compile(QUOTATION_PLACEHOLDER);

    private JavaLocalizationEntryParser() {
    }

    /// @return the localization keys - the first argument of each call, unquoted
    static List<String> getLanguageKeysInString(String content) {
        List<String> result = new ArrayList<>();

        for (String parameter : getLocalizationParameter(content)) {
            String languageKey = getContentWithinQuotes(parameter);
            if (languageKey.contains("\\\n") || languageKey.contains("\\\\n")) {
                // A newline in a key is stored as the two characters "\n" in the .properties
                // file, so the Java literal must carry a single backslash too.
                // See also https://stackoverflow.com/a/10285687/873282
                throw new IllegalStateException("\"" + languageKey
                        + "\" contains an escaped new line character. The newline character has to be written "
                        + "with a single backslash, not with a double one: \\n is correct, \\\\n is wrong.");
            }

            String languagePropertyKey = LocalizationKey.fromEscapedJavaString(languageKey).getKey();

            if (languagePropertyKey.endsWith(" ")) {
                throw new IllegalStateException("\"" + languageKey
                        + "\" ends with a space. As this is a localization key, this is illegal!");
            }

            if (!languagePropertyKey.isBlank()) {
                result.add(languagePropertyKey);
            }
        }

        return result;
    }

    private static String getContentWithinQuotes(String parameter) {
        // Protect \" so it does not end the literal.
        String protectedParameter = ESCAPED_QUOTATION_SYMBOL.matcher(parameter).replaceAll(QUOTATION_PLACEHOLDER);

        StringBuilder builder = new StringBuilder();
        int quotations = 0;
        for (char currentCharacter : protectedParameter.toCharArray()) {
            if ((currentCharacter == '"') && (quotations > 0)) {
                quotations--;
            } else if (currentCharacter == '"') {
                quotations++;
            } else if (quotations != 0) {
                builder.append(currentCharacter);
            } else if (currentCharacter == ',') {
                // The key is the first argument; the rest are the %0, %1, ... values.
                break;
            }
        }

        return QUOTATION_SYMBOL_PATTERN.matcher(builder.toString()).replaceAll("\\\"");
    }

    /// @return the full argument list of each call, as written in the source
    static List<String> getLocalizationParameter(String rawContent) {
        List<String> result = new ArrayList<>();

        // Comments may contain `Localization.lang(...)` snippets, which are no real usages.
        String content = blankOutComments(rawContent);

        Matcher matcher = LOCALIZATION_START_PATTERN.matcher(content);
        while (matcher.find()) {
            // Find the contents between the brackets, covering multi-line calls as well.
            int index = matcher.end();
            int brackets = 1;
            StringBuilder buffer = new StringBuilder();
            while (brackets != 0) {
                char c = content.charAt(index);
                if (c == '(') {
                    brackets++;
                } else if (c == ')') {
                    brackets--;
                }
                if (brackets != 0) {
                    buffer.append(c);
                }
                index++;
            }
            result.add(buffer.toString().trim());
        }

        return result;
    }

    /// Replaces the content of all Java comments (`//`, `///`, `/* */`, javadoc) by spaces.
    /// Newlines are kept, so that the result has the same length as the input and the same
    /// line structure. String literals, text blocks, and character literals are left untouched,
    /// so a `//` inside a string (e.g. a URL) does not start a comment.
    static String blankOutComments(String source) {
        char[] chars = source.toCharArray();

        CommentState state = CommentState.NORMAL;
        char quote = '\0';

        for (int i = 0; i < chars.length; i++) {
            switch (state) {
                case NORMAL -> {
                    if (startsWith(chars, i, '/', '/')) {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i++;
                        state = CommentState.LINE_COMMENT;
                    } else if (startsWith(chars, i, '/', '*')) {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i++;
                        state = CommentState.BLOCK_COMMENT;
                    } else if (startsTextBlock(chars, i)) {
                        i += 2;
                        state = CommentState.TEXT_BLOCK;
                    } else if (chars[i] == '"') {
                        quote = '"';
                        state = CommentState.STRING;
                    } else if (chars[i] == '\'') {
                        quote = '\'';
                        state = CommentState.CHARACTER;
                    }
                }

                case LINE_COMMENT -> {
                    if (chars[i] == '\n') {
                        state = CommentState.NORMAL;
                    } else {
                        chars[i] = ' ';
                    }
                }

                case BLOCK_COMMENT -> {
                    if (startsWith(chars, i, '*', '/')) {
                        chars[i] = ' ';
                        chars[i + 1] = ' ';
                        i++;
                        state = CommentState.NORMAL;
                    } else if (chars[i] != '\n') {
                        chars[i] = ' ';
                    }
                }

                case STRING,
                     CHARACTER -> {
                    if (chars[i] == '\\') {
                        i++; // skip the escaped character
                    } else if (chars[i] == quote) {
                        state = CommentState.NORMAL;
                    }
                }

                case TEXT_BLOCK -> {
                    if (chars[i] == '\\') {
                        i++; // skip the escaped character
                    } else if (startsTextBlock(chars, i)) {
                        i += 2;
                        state = CommentState.NORMAL;
                    }
                }

                default -> throw new IllegalStateException("Unhandled state " + state);
            }
        }

        return new String(chars);
    }

    private static boolean startsWith(char[] chars, int index, char first, char second) {
        return (index + 1 < chars.length)
                && (chars[index] == first)
                && (chars[index + 1] == second);
    }

    private static boolean startsTextBlock(char[] chars, int index) {
        return (index + 2 < chars.length)
                && (chars[index] == '"')
                && (chars[index + 1] == '"')
                && (chars[index + 2] == '"');
    }
}
