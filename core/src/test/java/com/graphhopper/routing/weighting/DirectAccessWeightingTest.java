package com.graphhopper.routing.weighting;

import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.weighting.DirectAccessWeighting.extract;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectAccessWeightingTest {

    @Test
    public void testExtract1Byte() {
        assertEquals(0b10101, extract(0b01010100, 5, 2));
        assertEquals(0b010010, extract(0b01001011, 6, 2));
    }

    @Test
    public void testExtract2Bytes() {
        assertEquals(0b1001, extract(0b01010110, 0b01010100, 4, 6));
        assertEquals(0b00101, extract(0b01001001, 0b01001000, 5, 6));
    }

    @Test
    public void testExtract3Bytes() {
        assertEquals(0b11001010100010, extract(0b01010110, 0b01010100, 0b01010110, 14, 5));
    }

}
