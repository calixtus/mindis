package org.mindis.core.export;

import java.util.List;

/// The document elements a plan template may produce, flattened out of the
/// Markdown syntax tree by [PlanBlocks]. Every renderer draws this list and
/// none of them knows Markdown.
///
/// <p>The set is deliberately small: it is exactly what the text formats and
/// the PDF page painter can both express.
public sealed interface PlanBlock {

    /// A run of text with the emphasis that applies to it.
    record Span(String text, boolean bold, boolean italic) {

        public static Span plain(String text) {
            return new Span(text, false, false);
        }
    }

    /// `#` to `######`; `level` is 1-based.
    record Heading(int level, List<Span> spans) implements PlanBlock {
        public Heading {
            spans = List.copyOf(spans);
        }
    }

    record Paragraph(List<Span> spans) implements PlanBlock {
        public Paragraph {
            spans = List.copyOf(spans);
        }
    }

    /// One bullet of a list; nested lists are flattened onto `depth`.
    record BulletItem(int depth, List<Span> spans) implements PlanBlock {
        public BulletItem {
            spans = List.copyOf(spans);
        }
    }

    /// A GFM table. The header row may be empty, the body may have none.
    record Table(List<String> headers, List<List<String>> rows) implements PlanBlock {
        public Table {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    /// An image the renderers resolve themselves; `destination` is
    /// [PlanTemplate#LOGO_DESTINATION] for the parish logo.
    record Image(String alt, String destination) implements PlanBlock {
    }

    /// A `---` rule: a page break in the PDF, blank space everywhere else.
    record Break() implements PlanBlock {
    }
}
