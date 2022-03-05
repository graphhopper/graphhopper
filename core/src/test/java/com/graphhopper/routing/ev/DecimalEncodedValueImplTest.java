package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import java.util.Random;

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

    @Test
    public void testNextStorableValue() {
        DecimalEncodedValueImpl enc = new DecimalEncodedValueImpl("test", 4, 3, false);
        enc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);

        // some values can be stored...
        enc.setDecimal(false, intsRef, 3);
        assertEquals(3, enc.getDecimal(false, intsRef));
        // ... and some cannot:
        enc.setDecimal(false, intsRef, 5);
        assertEquals(6, enc.getDecimal(false, intsRef));

        // getNextStorableValue tells us the next highest value we can store without such modification between set/get
        assertEquals(0, enc.getNextStorableValue(0));
        assertEquals(3, enc.getNextStorableValue(0.1));
        assertEquals(3, enc.getNextStorableValue(1.5));
        assertEquals(3, enc.getNextStorableValue(2.9));
        assertEquals(3, enc.getNextStorableValue(3));
        assertEquals(6, enc.getNextStorableValue(3.1));
        assertEquals(6, enc.getNextStorableValue(4.5));
        assertEquals(6, enc.getNextStorableValue(5.9));
        assertEquals(45, enc.getNextStorableValue(44.3));
        assertEquals(45, enc.getNextStorableValue(45));
        // for values higher than 3*15=45 there is no next storable value, and we get an error
        assertThrows(IllegalArgumentException.class, () -> enc.getNextStorableValue(46));

        // check random values in [0, 45]
        Random rnd = new Random();
        for (int i = 0; i < 1000; i++) {
            double value = rnd.nextDouble() * 45;
            double nextStorable = enc.getNextStorableValue(value);
            assertTrue(nextStorable >= value, "next storable value should be larger than the value");
            enc.setDecimal(false, intsRef, nextStorable);
            assertEquals(nextStorable, enc.getDecimal(false, intsRef), "next storable value should be returned without modification");
        }
    }

    @Test
    public void testNextStorableValue_maxInfinity() {
        DecimalEncodedValueImpl enc = new DecimalEncodedValueImpl("test", 4, 0, 3, false, false, false, true);
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(12, enc.getNextStorableValue(11.2));
        assertEquals(45, enc.getNextStorableValue(44.3));
        assertEquals(45, enc.getNextStorableValue(45));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(45.1));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(48));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(48.1));
        IntsRef intsRef = new IntsRef(1);
        assertThrows(IllegalArgumentException.class, () -> enc.setDecimal(false, intsRef, 48));
        enc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, enc.getDecimal(false, intsRef));
    }

}