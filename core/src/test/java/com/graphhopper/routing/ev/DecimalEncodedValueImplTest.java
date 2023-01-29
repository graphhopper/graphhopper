package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DecimalEncodedValueImplTest {

    @Test
    public void getDecimal() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, 1, false);
        testEnc.init(new EncodedValue.InitializerConfig());

        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, 7);
        assertEquals(7, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void setMaxToInfinity() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, 0, 1, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        assertEquals(0, testEnc.getDecimal(false, intsRef), .1);

        assertTrue(Double.isInfinite(testEnc.getMaxOrMaxStorableDecimal()));
        assertTrue(Double.isInfinite(testEnc.getMaxStorableDecimal()));
        assertTrue(Double.isInfinite(testEnc.getNextStorableValue(7)));
        assertEquals(6, testEnc.getNextStorableValue(6));

        testEnc.setDecimal(false, intsRef, 5);
        assertEquals(5, testEnc.getDecimal(false, intsRef), .1);

        assertEquals(5, testEnc.getMaxOrMaxStorableDecimal());
        assertTrue(Double.isInfinite(testEnc.getMaxStorableDecimal()));

        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
        assertTrue(Double.isInfinite(testEnc.getMaxOrMaxStorableDecimal()));
        assertTrue(Double.isInfinite(testEnc.getMaxStorableDecimal()));
    }

    @Test
    public void testNegative() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, -6, 0.1, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        // a bit ugly: the default is the minimum not 0
        assertEquals(-6, testEnc.getDecimal(false, intsRef), .1);

        testEnc.setDecimal(false, intsRef, -5.5);
        assertEquals(-5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);

        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            new DecimalEncodedValueImpl("test", 3, -6, 0.11, false, false, true);
        });
        assertTrue(e.getMessage().contains("minStorableValue -6.0 is not a multiple of the specified factor"), e.getMessage());
    }

    @Test
    public void testInfinityWithMinValue() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 3, -6, 0.1, false, false, true);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testNegateReverse() {
        DecimalEncodedValueImpl testEnc = new DecimalEncodedValueImpl("test", 4, 0, 0.5, true, false, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, 5.5);
        assertEquals(5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-5.5, testEnc.getDecimal(true, intsRef), .1);

        testEnc.setDecimal(false, intsRef, -5.5);
        assertEquals(-5.5, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(5.5, testEnc.getDecimal(true, intsRef), .1);

        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        new DecimalEncodedValueImpl("tmp1", 5, 1, false).init(config);
        testEnc = new DecimalEncodedValueImpl("tmp2", 5, 0, 1, true, false, false);
        testEnc.init(config);
        intsRef = new IntsRef(1);
        testEnc.setDecimal(true, intsRef, 2.6);
        assertEquals(-3, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(3, testEnc.getDecimal(true, intsRef), .1);

        testEnc.setDecimal(true, intsRef, -2.6);
        assertEquals(3, testEnc.getDecimal(false, intsRef), .1);
        assertEquals(-3, testEnc.getDecimal(true, intsRef), .1);
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
    public void smallestNonZeroValue() {
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 5, 10, true), 10);
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 10, 10, true), 10);
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 5, 5, true), 5);
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 5, 1, true), 1);
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 5, 0.5, true), 0.5);
        assertSmallestNonZeroValue(new DecimalEncodedValueImpl("test", 5, 0.1, true), 0.1);

        assertTrue(assertThrows(IllegalStateException.class,
                () -> new DecimalEncodedValueImpl("test", 5, 0, 5, true, false, false).getSmallestNonZeroValue())
                .getMessage().contains("getting the smallest non-zero value is not possible"));
    }

    private void assertSmallestNonZeroValue(DecimalEncodedValueImpl enc, double expected) {
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(expected, enc.getSmallestNonZeroValue());
        IntsRef intsRef = new IntsRef(1);
        enc.setDecimal(false, intsRef, enc.getSmallestNonZeroValue());
        assertEquals(expected, enc.getDecimal(false, intsRef));
        enc.setDecimal(false, intsRef, enc.getSmallestNonZeroValue() / 2 - 0.01);
        assertEquals(0, enc.getDecimal(false, intsRef));
    }

    @Test
    public void testNextStorableValue_maxInfinity() {
        DecimalEncodedValueImpl enc = new DecimalEncodedValueImpl("test", 4, 0, 3, false, false, true);
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(12, enc.getNextStorableValue(11.2));
        assertEquals(42, enc.getNextStorableValue(41.3));
        assertEquals(42, enc.getNextStorableValue(42));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(42.1));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(45));
        assertEquals(Double.POSITIVE_INFINITY, enc.getNextStorableValue(45.1));
        IntsRef intsRef = new IntsRef(1);
        enc.setDecimal(false, intsRef, 45);
        assertEquals(42, enc.getDecimal(false, intsRef));

        enc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, enc.getDecimal(false, intsRef));
    }

    @Test
    public void lowestUpperBound_with_negateReverseDirection() {
        DecimalEncodedValueImpl enc = new DecimalEncodedValueImpl("test", 4, 0, 3, true, false, false);
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(15 * 3, enc.getMaxOrMaxStorableDecimal());
        IntsRef ints = new IntsRef(1);
        enc.setDecimal(false, ints, 3);
        assertEquals(3, enc.getDecimal(false, ints));
        assertEquals(3, enc.getMaxOrMaxStorableDecimal());
        enc.setDecimal(true, ints, -6);
        assertEquals(6, enc.getDecimal(false, ints));
        assertEquals(6, enc.getMaxOrMaxStorableDecimal());
        // note that the maximum is never lowered, even when we lower the value for the 'same' edge flags
        enc.setDecimal(false, ints, 0);
        assertEquals(0, enc.getDecimal(false, ints));
        assertEquals(6, enc.getMaxOrMaxStorableDecimal());
    }

    @Test
    public void minStorableBug() {
        DecimalEncodedValue enc = new DecimalEncodedValueImpl("test", 5, -3, 0.2, false, true, false);
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(3.2, enc.getMaxStorableDecimal());

        IntsRef flags = new IntsRef(1);
        enc.setDecimal(true, flags, 1.6);
        assertEquals(1.6, enc.getDecimal(true, flags));
    }
}