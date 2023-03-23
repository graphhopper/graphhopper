package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaxWeightTest {

    @Test
    public void testSetAndGet() {
        DecimalEncodedValue mappedDecimalEnc = MaxWeight.create();
        mappedDecimalEnc.init(new EncodedValue.InitializerConfig());
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        mappedDecimalEnc.setDecimal(false, edgeId, edgeIntAccess, 20);
        assertEquals(20, mappedDecimalEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        mappedDecimalEnc.setDecimal(false, edgeId, edgeIntAccess, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, mappedDecimalEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }
}