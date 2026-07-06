package org.mindis.core.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppLanguageTest {

    @Test
    void tagRoundTrip() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"));
    }

    @Test
    void unknownTagFallsBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("fr"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(""));
    }

    @Test
    void displayNamesAreNeverTranslated() {
        assertEquals("English", AppLanguage.ENGLISH.displayName());
        assertEquals("Deutsch", AppLanguage.GERMAN.displayName());
    }
}
