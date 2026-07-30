package org.mindis.core.persistence;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Translates one entity to and from one CSV row. Implemented per entity
/// ([RoleCsvMapper], [ServerCsvMapper], [ServiceCsvMapper],
/// [TemplateCsvMapper]) and consumed by whatever offers import/export -
/// currently the GUI's CRUD modules, and PLAN.md's future web module for free.
///
/// Lives in core beside its implementations rather than with its UI consumer,
/// so the mappers implement it directly; a UI-side interface would force every
/// call site through an adapter that does nothing but forward three methods.
///
/// @param <T> the entity type
@NullMarked
public interface CsvRowMapper<T> {

    /// Column names, written as the first row on export.
    List<String> header();

    /// One item as a row of field values, in [#header()] order.
    List<String> toRow(T item);

    /// One row (excluding the header) into an item, or `null` to skip
    /// the row (e.g. blank or unparsable). Fields beyond the row's length are
    /// treated as absent by the mapper, not an error - CSV rows may be
    /// shorter than the header if trailing columns were left blank.
    @Nullable T fromRow(List<String> row);
}
