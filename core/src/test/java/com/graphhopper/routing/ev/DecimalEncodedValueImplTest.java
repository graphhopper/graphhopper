package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DecimalEncodedValueImplTest {

    @Test
    public void getDecimal() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, 1, false, false);
        testEnc.init(new EncodedValue.InitializerConfig());

        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, 7);
        assertEquals(7, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testInfinityDefault() {
        IntsRef intsRef = new IntsRef(1);
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, 1, true, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));

        // set the default which maps to infinity (see discussion in #2473)
        testEnc.setDecimal(false, intsRef, 0);
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));

        assertTrue(Double.MAX_VALUE < testEnc.getDecimal(false, intsRef));
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));
    }

    @Test
    public void setMaxToInfinity() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, 0, 1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testNegative() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, -6, 0.1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, -5.5);
        assertEquals(-5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);

        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            new DecimalEncodedValueImpl("test", 3, -6, 0.11, false, false, false, true);
        });
        assertTrue(e.getMessage().contains("minValue -6.0 is not a multiple of the specified factor"), e.getMessage());
    }

    @Test
    public void testInfinityWithMinValue() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, -6, 0.1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testNegateReverse() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 4, 0, 0.5, false, true, false, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, 5.5);
        assertEquals(5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);
    }
}