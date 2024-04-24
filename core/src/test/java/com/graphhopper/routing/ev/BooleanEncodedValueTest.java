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
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        bool.setBool(false, edgeId, edgeAccess, false);
        assertFalse(bool.getBool(false, edgeId, edgeAccess));
        bool.setBool(false, edgeId, edgeAccess, true);
        assertTrue(bool.getBool(false, edgeId, edgeAccess));
    }

    @Test
    public void testBitDirected() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        BooleanEncodedValue bool = new SimpleBooleanEncodedValue("access", true);
        bool.init(config);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        bool.setBool(false, edgeId, edgeAccess, false);
        bool.setBool(true, edgeId, edgeAccess, true);

        assertFalse(bool.getBool(false, edgeId, edgeAccess));
        assertTrue(bool.getBool(true, edgeId, edgeAccess));
    }
}
