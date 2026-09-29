package org.mindis.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// A date field shows ISO but has to accept what the user types, and the rest
/// of the app shows dates in their own locale - so a German user types
/// "30.07.2026". That used to parse to `null`, leaving the picker valueless
/// while the typed text sat there looking accepted (and every "Add" acting on
/// that value silently doing nothing).
class CalendarPickersTest {

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void parsesIsoWhateverTheLocale() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("2026-07-30"));
        Locale.setDefault(Locale.US);
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("2026-07-30"));
    }

    @Test
    void parsesTheLocalesOwnFormat() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("30.07.2026"));
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("30.07.26"));
        // One-digit day and month, as they get typed in practice.
        assertEquals(LocalDate.of(2026, 9, 5), CalendarPickers.parse("5.9.2026"));
        assertEquals(LocalDate.of(2026, 9, 5), CalendarPickers.parse("5.9.26"));
    }

    @Test
    void parsesTheEnglishFormatsToo() {
        Locale.setDefault(Locale.US);
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("Jul 30, 2026"));
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("7/30/26"));
        assertEquals(LocalDate.of(2026, 7, 30), CalendarPickers.parse("7/30/2026"));
    }

    @Test
    void readsAnAmbiguousDateTheWayTheLocaleReadsIt() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals(LocalDate.of(2026, 4, 3), CalendarPickers.parse("3.4.2026"));
        Locale.setDefault(Locale.US);
        assertEquals(LocalDate.of(2026, 3, 4), CalendarPickers.parse("3/4/2026"));
    }

    @Test
    void blankAndNonsenseStayNull() {
        Locale.setDefault(Locale.GERMANY);
        assertNull(CalendarPickers.parse(null));
        assertNull(CalendarPickers.parse("   "));
        assertNull(CalendarPickers.parse("next Tuesday"));
        assertNull(CalendarPickers.parse("30.13.2026"));
    }
}
