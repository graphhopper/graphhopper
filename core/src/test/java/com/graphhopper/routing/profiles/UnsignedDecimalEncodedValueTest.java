package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class UnsignedDecimalEncodedValueTest {

    @Test
    public void getDecimal() {
        UnsignedDecimalEncodedValue testEnc = new UnsignedDecimalEncodedValue("test", 3, 1, 100, false);
        testEnc.init(new EncodedValue.InitializerConfig());

        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, 7);
        assertEquals(7, testEnc.getDecimal(false, intsRef), .1);
    }

    @Test
    public void testDefault() {
        // default value 100
        UnsignedDecimalEncodedValue testEnc = new UnsignedDecimalEncodedValue("test", 3, 1, 100, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        testEnc.setDecimal(false, intsRef, 0);
        assertEquals(100, testEnc.getDecimal(false, intsRef), .1);

        // try positive infinity
        testEnc = new UnsignedDecimalEncodedValue("test", 3, 1, Double.POSITIVE_INFINITY, false);
        testEnc.init(new EncodedValue.InitializerConfig());
        testEnc.setDecimal(false, intsRef, 0);
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));
        assertTrue(Double.MAX_VALUE < testEnc.getDecimal(false, intsRef));
        testEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertTrue(Double.isInfinite(testEnc.getDecimal(false, intsRef)));
    }
}