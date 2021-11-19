package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SignedDecimalEncodedValueTest {

    @Test
    public void getDecimal() {
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 3, 1, false, false);
        testEnc.init(new EncodedValue.InitializerConfig());

        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, 7);
        assertEquals(7, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testInfinityDefault() {
        IntsRef intsRef = new IntsRef(1);
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 3, 1, true, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));

        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            testEnc.setDecimal(false, intsRef, 0);
        });
        assertTrue(e.getMessage().contains("0 cannot be explicitly used when defaultIsInfinity is true"), e.getMessage());

        assertTrue(Double.MAX_VALUE < testEnc.getDecimal(false, intsRef));
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));
    }

    @Test
    public void setMaxToInfinity() {
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 3, 0, 1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testNegative() {
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 3, -6, 0.1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, -5.5);
        assertEquals(-5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);

        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            new SignedDecimalEncodedValue("test", 3, -6, 0.11, false, false, false, true);
        });
        assertTrue(e.getMessage().contains("minValue -6.0 is not a multiple of the specified factor"), e.getMessage());
    }

    @Test
    public void testInfinityWithMinValue() {
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 3, -6, 0.1, false, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testNegateReverse() {
        SignedDecimalEncodedValue testEnc = new SignedDecimalEncodedValue("test", 4, 0, 0.5, false, true, false, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, 5.5);
        assertEquals(5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);
    }
}