package org.mindis.core.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// Hand-rolled minimal RTF writer - no RTF library is pulled in for this.
/// Headings become bold runs at a larger size, tables become tab-separated
/// rows, and an image is embedded as a `\pngblip` picture.
final class RtfPlanRenderer implements PlanRenderer {

    private static final int[] HEADING_HALF_POINTS = {32, 24, 22, 20};
    private static final int BODY_HALF_POINTS = 20;
    /// Widest a logo may be drawn, in twips (1/1440 inch); 2400 = 120 pt.
    private static final int MAX_IMAGE_WIDTH_TWIPS = 2400;
    /// Screen pixels are treated as 96 dpi when converting to twips.
    private static final int TWIPS_PER_PIXEL = 15;

    @Override
    public PlanExportFormat format() {
        return PlanExportFormat.RTF;
    }

    @Override
    public void render(RenderedPlan plan, Path targetFile) {
        StringBuilder rtf = new StringBuilder();
        rtf.append("{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Helvetica;}}\\f0\n");

        for (PlanBlock block : plan.blocks()) {
            switch (block) {
                case PlanBlock.Heading heading -> {
                    int size = HEADING_HALF_POINTS[Math.min(heading.level(), HEADING_HALF_POINTS.length) - 1];
                    rtf.append("\\b\\fs").append(size).append(' ');
                    appendSpans(rtf, heading.spans());
                    rtf.append("\\b0\\fs").append(BODY_HALF_POINTS).append("\\par\n");
                }
                case PlanBlock.Paragraph paragraph -> {
                    appendSpans(rtf, paragraph.spans());
                    rtf.append("\\par\n");
                }
                case PlanBlock.BulletItem item -> {
                    rtf.append("\\tab ".repeat(item.depth() - 1)).append("\\bullet\\tab ");
                    appendSpans(rtf, item.spans());
                    rtf.append("\\par\n");
                }
                case PlanBlock.Table table -> appendTable(rtf, table);
                case PlanBlock.Image image -> appendImage(rtf, image, plan.logoPng());
                case PlanBlock.Break ignored -> rtf.append("\\par\n");
            }
        }

        rtf.append("}");

        try {
            // US-ASCII: everything outside it is already written as RTF
            // unicode escapes by appendEscaped.
            Files.writeString(targetFile, rtf.toString(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write RTF: " + targetFile, e);
        }
    }

    private static void appendTable(StringBuilder rtf, PlanBlock.Table table) {
        if (!table.headers().isEmpty()) {
            rtf.append("\\b ");
            appendCells(rtf, table.headers());
            rtf.append("\\b0\\par\n");
        }
        for (List<String> row : table.rows()) {
            appendCells(rtf, row);
            rtf.append("\\par\n");
        }
        rtf.append("\\par\n");
    }

    private static void appendCells(StringBuilder rtf, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                rtf.append("\\tab ");
            }
            appendEscaped(rtf, cells.get(i));
        }
    }

    private static void appendImage(StringBuilder rtf, PlanBlock.Image image, byte @Nullable [] logoPng) {
        if (!PlanTemplate.LOGO_DESTINATION.equals(image.destination()) || logoPng == null) {
            if (!image.alt().isEmpty()) {
                appendEscaped(rtf, image.alt());
                rtf.append("\\par\n");
            }
            return;
        }
        PngSize size = PngSize.read(logoPng);
        if (size == null) {
            return;
        }
        int widthTwips = size.width() * TWIPS_PER_PIXEL;
        int heightTwips = size.height() * TWIPS_PER_PIXEL;
        if (widthTwips > MAX_IMAGE_WIDTH_TWIPS) {
            heightTwips = (int) ((long) heightTwips * MAX_IMAGE_WIDTH_TWIPS / widthTwips);
            widthTwips = MAX_IMAGE_WIDTH_TWIPS;
        }
        rtf.append("{\\pict\\pngblip")
                .append("\\picw").append(size.width())
                .append("\\pich").append(size.height())
                .append("\\picwgoal").append(widthTwips)
                .append("\\pichgoal").append(heightTwips)
                .append('\n');
        for (byte b : logoPng) {
            rtf.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        rtf.append("}\\par\n");
    }

    private static void appendSpans(StringBuilder rtf, List<PlanBlock.Span> spans) {
        for (PlanBlock.Span span : spans) {
            if (span.bold()) {
                rtf.append("\\b ");
            }
            if (span.italic()) {
                rtf.append("\\i ");
            }
            appendEscaped(rtf, span.text());
            if (span.italic()) {
                rtf.append("\\i0 ");
            }
            if (span.bold()) {
                rtf.append("\\b0 ");
            }
        }
    }

    private static void appendEscaped(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '{' || c == '}') {
                out.append('\\').append(c);
            } else if (c < 128) {
                out.append(c);
            } else {
                out.append("\\u").append((int) c).append('?');
            }
        }
    }

    /// The pixel dimensions out of a PNG's IHDR chunk, which both RTF picture
    /// properties need.
    private record PngSize(int width, int height) {

        private static final int IHDR_WIDTH_OFFSET = 16;

        static @Nullable PngSize read(byte[] png) {
            if (png.length < IHDR_WIDTH_OFFSET + 8) {
                return null;
            }
            int width = readInt(png, IHDR_WIDTH_OFFSET);
            int height = readInt(png, IHDR_WIDTH_OFFSET + 4);
            return width <= 0 || height <= 0 ? null : new PngSize(width, height);
        }

        private static int readInt(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 24)
                    | ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
        }
    }
}
