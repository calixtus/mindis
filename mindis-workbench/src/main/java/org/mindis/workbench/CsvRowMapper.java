package org.mindis.workbench;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Translates a {@link CrudModule} item to and from one CSV row. Implement and
 * return from {@link CrudModule#csvMapper()} to enable the module's Export /
 * Import toolbar buttons.
 *
 * @param <T> the item type, matching the owning {@code CrudModule<T>}
 */
public interface CsvRowMapper<T> {

    /** Column names, written as the first row on export. */
    List<String> header();

    /** One item as a row of field values, in {@link #header()} order. */
    List<String> toRow(T item);

    /**
     * One row (excluding the header) into an item, or {@code null} to skip
     * the row (e.g. blank or unparsable). Fields beyond the row's length are
     * treated as absent by the mapper, not an error - CSV rows may be
     * shorter than the header if trailing columns were left blank.
     */
    T fromRow(List<String> row);

    /** Builds a mapper from three functions, e.g. viewmodel method references. */
    static <T> CsvRowMapper<T> of(Supplier<List<String>> header,
                                  Function<T, List<String>> toRow,
                                  Function<List<String>, T> fromRow) {
        return new CsvRowMapper<>() {
            @Override
            public List<String> header() {
                return header.get();
            }

            @Override
            public List<String> toRow(T item) {
                return toRow.apply(item);
            }

            @Override
            public T fromRow(List<String> row) {
                return fromRow.apply(row);
            }
        };
    }
}
