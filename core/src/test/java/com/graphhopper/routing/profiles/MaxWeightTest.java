package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MaxWeightTest {

    @Test
    public void testSetAndGet() {
        DecimalEncodedValue mappedDecimalEnc = MaxWeight.create();
        mappedDecimalEnc.init(new EncodedValue.InitializerConfig());
        IntsRef intsRef = new IntsRef(1);
        mappedDecimalEnc.setDecimal(false, intsRef, 20);
        assertEquals(20, mappedDecimalEnc.getDecimal(false, intsRef), .1);

        intsRef = new IntsRef(1);
        mappedDecimalEnc.setDecimal(false, intsRef, 0);
        assertEquals(Double.POSITIVE_INFINITY, mappedDecimalEnc.getDecimal(false, intsRef), .1);

        mappedDecimalEnc.setDecimal(false, intsRef, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, mappedDecimalEnc.getDecimal(false, intsRef), .1);
    }
}