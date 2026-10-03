package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

class CsvIOTest {

    @Test
    void writeThenParse_roundTripsFieldsThatNeedQuoting() throws IOException {
        List<String> header = List.of("name", "note");
        List<List<String>> rows = List.of(
                List.of("Anna", "plain"),
                List.of("Becker, Ben", "says \"hi\""),
                List.of("Cara", "two\nlines"));
        StringWriter out = new StringWriter();

        CsvIO.write(out, header, rows);

        assertEquals(List.of(header, rows.getFirst(), rows.get(1), rows.get(2)), CsvIO.parse(out.toString()));
    }

    /// Maps a row to its first field, skipping rows whose first field is blank.
    private static final CsvRowMapper<String> FIRST_FIELD = new CsvRowMapper<>() {
        @Override
        public List<String> header() {
            return List.of("value");
        }

        @Override
        public List<String> toRow(String item) {
            return List.of(item);
        }

        @Override
        public @Nullable String fromRow(List<String> row) {
            return row.isEmpty() || row.getFirst().isBlank() ? null : row.getFirst();
        }
    };

    @Test
    void read_mapsDataRowsAndCountsSkippedOnes() throws IOException {
        StringWriter out = new StringWriter();
        CsvIO.write(out, FIRST_FIELD, List.of("a", " ", "b"));

        CsvIO.Import<String> imported = CsvIO.read(out.toString(), FIRST_FIELD);

        assertEquals(List.of("a", "b"), imported.items());
        assertEquals(3, imported.rowCount());
    }

    @Test
    void parse_keepsAnUnterminatedLastRow() {
        assertEquals(List.of(List.of("a", "b"), List.of("c", "")), CsvIO.parse("a,b\r\nc,"));
    }
}
