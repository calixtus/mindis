package org.mindis.core.export;

/// Escapes the Markdown characters that change meaning wherever they appear:
/// emphasis, code, links, angle brackets, and the pipe that would split a table
/// cell in two. Applied to every template value unless the template opts out
/// with `{{ value | raw }}`.
///
/// <p>Block markers (`#`, `-`, `+`, `>`) are deliberately left alone. They only
/// mean anything at the start of a line, which is a position the template
/// author controls; escaping them defensively would print `\-` and
/// `2\. 8\. 2026` in the Markdown export itself.
final class MarkdownEscaper {

    private static final String INLINE_SYNTAX = "\\`*_[]<>|";

    private MarkdownEscaper() {
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (INLINE_SYNTAX.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
