package org.mindis.core.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PngTest {

    @Test
    void changingTheSourceOrTheReturnedArrayLeavesTheImageAlone() {
        byte[] source = {1, 2, 3};
        Png png = Png.of(source);

        source[0] = 9;
        png.bytes()[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, png.bytes());
    }

    @Test
    void equalBytesMakeEqualImages() {
        assertEquals(Png.of(new byte[] {1, 2}), Png.of(new byte[] {1, 2}));
        assertEquals(new ParishIdentity("St. Mary", Png.of(new byte[] {1, 2})),
                new ParishIdentity("St. Mary", Png.of(new byte[] {1, 2})));
    }
}
