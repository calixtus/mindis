package org.mindis.workbench;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/// Minimal RFC 4180 CSV read/write: comma-separated, double-quote escaping,
/// CRLF line endings on write. No external dependency - {@link CrudModule}'s
/// only consumer, and the format is simple enough not to need one.
final class CsvIO {

    private CsvIO() {
    }

    static void write(Writer writer, List<String> header, List<List<String>> rows) throws IOException {
        writeRow(writer, header);
        for (List<String> row : rows) {
            writeRow(writer, row);
        }
    }

    private static void writeRow(Writer writer, List<String> fields) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(fields.get(i)));
        }
        line.append("\r\n");
        writer.write(line.toString());
    }

    private static String escape(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0
                && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    /// Parses full CSV file content into rows of fields; unterminated trailing rows are included.
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;
        int i = 0;
        int length = content.length();
        while (i < length) {
            char c = content.charAt(i);
            if (inQuotes) {
                rowHasContent = true;
                if (c == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    rowHasContent = true;
                }
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    rowHasContent = true;
                }
                case '\r' -> {
                    // Ignored; the paired '\n' ends the row.
                }
                case '\n' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    rowHasContent = false;
                }
                default -> {
                    field.append(c);
                    rowHasContent = true;
                }
            }
            i++;
        }
        if (rowHasContent || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
