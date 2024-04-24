package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaxWeightTest {

    @Test
    public void testSetAndGet() {
        DecimalEncodedValue mappedDecimalEnc = MaxWeight.create();
        mappedDecimalEnc.init(new EncodedValue.InitializerConfig());
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        mappedDecimalEnc.setDecimal(false, edgeId, edgeAccess, 20);
        assertEquals(20, mappedDecimalEnc.getDecimal(false, edgeId, edgeAccess), .1);

        edgeAccess = new EdgeBytesAccessArray(4);
        mappedDecimalEnc.setDecimal(false, edgeId, edgeAccess, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, mappedDecimalEnc.getDecimal(false, edgeId, edgeAccess), .1);
    }
}
