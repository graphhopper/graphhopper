package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BooleanEncodedValueTest {

    @Test
    public void testBit() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        IntEncodedValue intProp = new IntEncodedValueImpl("somevalue", 5, false);
        intProp.init(config);

        BooleanEncodedValue bool = new SimpleBooleanEncodedValue("access", false);
        bool.init(config);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        bool.setBool(false, edgeId, edgeIntAccess, false);
        assertFalse(bool.getBool(false, edgeId, edgeIntAccess));
        bool.setBool(false, edgeId, edgeIntAccess, true);
        assertTrue(bool.getBool(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testBitDirected() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        BooleanEncodedValue bool = new SimpleBooleanEncodedValue("access", true);
        bool.init(config);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        bool.setBool(false, edgeId, edgeIntAccess, false);
        bool.setBool(true, edgeId, edgeIntAccess, true);

        assertFalse(bool.getBool(false, edgeId, edgeIntAccess));
        assertTrue(bool.getBool(true, edgeId, edgeIntAccess));
    }
}