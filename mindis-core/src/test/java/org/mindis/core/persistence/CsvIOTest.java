package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

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

    @Test
    void parse_keepsAnUnterminatedLastRow() {
        assertEquals(List.of(List.of("a", "b"), List.of("c", "")), CsvIO.parse("a,b\r\nc,"));
    }
}
