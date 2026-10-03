package org.mindis.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IndexesTest {

    @Test
    void keepsTheInputOrder() {
        Map<Integer, String> byLength = Indexes.byKey(List.of("ccc", "a", "bb"), String::length);

        assertEquals(List.of(3, 1, 2), List.copyOf(byLength.keySet()));
    }

    @Test
    void aLaterDuplicateKeyWins() {
        Map<Integer, String> byLength = Indexes.byKey(List.of("ab", "cd"), String::length);

        assertEquals(Map.of(2, "cd"), byLength);
    }
}
