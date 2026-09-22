package org.mindis.core.export;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

/// Turns the Markdown a template produced into the flat [PlanBlock] list the
/// renderers draw. Everything Markdown can express that this list cannot -
/// links, HTML, nesting beyond bullets - collapses to its text content rather
/// than being dropped.
final class PlanBlocks {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private PlanBlocks() {
    }

    static List<PlanBlock> parse(String markdown) {
        List<PlanBlock> blocks = new ArrayList<>();
        appendBlocks(PARSER.parse(markdown), blocks, 0);
        return blocks;
    }

    private static void appendBlocks(Node parent, List<PlanBlock> out, int listDepth) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            switch (node) {
                case Heading heading -> out.add(new PlanBlock.Heading(heading.getLevel(), spans(heading)));
                case Paragraph paragraph -> appendParagraph(paragraph, out, listDepth);
                case ThematicBreak ignored -> out.add(new PlanBlock.Break());
                case BulletList list -> appendBlocks(list, out, listDepth + 1);
                case OrderedList list -> appendBlocks(list, out, listDepth + 1);
                case ListItem item -> appendBlocks(item, out, listDepth);
                case TableBlock table -> out.add(table(table));
                case FencedCodeBlock code -> out.add(paragraphOf(code.getLiteral()));
                case IndentedCodeBlock code -> out.add(paragraphOf(code.getLiteral()));
                case BlockQuote quote -> appendBlocks(quote, out, listDepth);
                default -> appendBlocks(node, out, listDepth);
            }
        }
    }

    /// A paragraph that is nothing but an image becomes an [PlanBlock.Image]
    /// the renderers can actually place; an image among text stays as its alt
    /// text.
    private static void appendParagraph(Paragraph paragraph, List<PlanBlock> out, int listDepth) {
        Node onlyChild = paragraph.getFirstChild();
        if (onlyChild instanceof Image image && onlyChild.getNext() == null) {
            out.add(new PlanBlock.Image(text(image), image.getDestination()));
            return;
        }
        List<PlanBlock.Span> spans = spans(paragraph);
        if (spans.isEmpty()) {
            return;
        }
        out.add(listDepth > 0
                ? new PlanBlock.BulletItem(listDepth, spans)
                : new PlanBlock.Paragraph(spans));
    }

    private static PlanBlock.Paragraph paragraphOf(String literal) {
        return new PlanBlock.Paragraph(List.of(PlanBlock.Span.plain(literal.strip())));
    }

    private static PlanBlock.Table table(TableBlock table) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (cell instanceof TableCell) {
                        cells.add(text(cell));
                    }
                }
                if (section instanceof TableHead && headers.isEmpty()) {
                    headers.addAll(cells);
                } else if (section instanceof TableBody || !(section instanceof TableHead)) {
                    rows.add(cells);
                }
            }
        }
        return new PlanBlock.Table(headers, rows);
    }

    /// The plain text of a node, emphasis flattened away.
    private static String text(Node node) {
        StringBuilder text = new StringBuilder();
        for (PlanBlock.Span span : spans(node)) {
            text.append(span.text());
        }
        return text.toString();
    }

    private static List<PlanBlock.Span> spans(Node parent) {
        List<PlanBlock.Span> spans = new ArrayList<>();
        collectSpans(parent, false, false, spans);
        trimEnds(spans);
        return spans;
    }

    private static void collectSpans(Node parent, boolean bold, boolean italic, List<PlanBlock.Span> out) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            switch (node) {
                case Text text -> append(out, text.getLiteral(), bold, italic);
                case Code code -> append(out, code.getLiteral(), bold, italic);
                case StrongEmphasis emphasis -> collectSpans(emphasis, true, italic, out);
                case Emphasis emphasis -> collectSpans(emphasis, bold, true, out);
                case SoftLineBreak ignored -> append(out, " ", bold, italic);
                case HardLineBreak ignored -> append(out, " ", bold, italic);
                case Image image -> append(out, text(image), bold, italic);
                case Link link -> collectSpans(link, bold, italic, out);
                default -> collectSpans(node, bold, italic, out);
            }
        }
    }

    private static void append(List<PlanBlock.Span> out, String text, boolean bold, boolean italic) {
        if (text.isEmpty()) {
            return;
        }
        if (!out.isEmpty()) {
            PlanBlock.Span last = out.getLast();
            if (last.bold() == bold && last.italic() == italic) {
                out.set(out.size() - 1, new PlanBlock.Span(last.text() + text, bold, italic));
                return;
            }
        }
        out.add(new PlanBlock.Span(text, bold, italic));
    }

    private static void trimEnds(List<PlanBlock.Span> spans) {
        if (spans.isEmpty()) {
            return;
        }
        PlanBlock.Span first = spans.getFirst();
        spans.set(0, new PlanBlock.Span(first.text().stripLeading(), first.bold(), first.italic()));
        PlanBlock.Span last = spans.getLast();
        spans.set(spans.size() - 1, new PlanBlock.Span(last.text().stripTrailing(), last.bold(), last.italic()));
        spans.removeIf(span -> span.text().isEmpty());
    }
}
