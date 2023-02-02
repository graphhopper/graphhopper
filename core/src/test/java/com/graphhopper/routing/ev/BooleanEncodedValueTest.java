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
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        bool.setBool(false, edgeId, intAccess, false);
        assertFalse(bool.getBool(false, edgeId, intAccess));
        bool.setBool(false, edgeId, intAccess, true);
        assertTrue(bool.getBool(false, edgeId, intAccess));
    }

    @Test
    public void testBitDirected() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        BooleanEncodedValue bool = new SimpleBooleanEncodedValue("access", true);
        bool.init(config);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        bool.setBool(false, edgeId, intAccess, false);
        bool.setBool(true, edgeId, intAccess, true);

        assertFalse(bool.getBool(false, edgeId, intAccess));
        assertTrue(bool.getBool(true, edgeId, intAccess));
    }
}