package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
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
        IntsRef ref = new IntsRef(1);
        bool.setBool(false, ref, false);
        assertFalse(bool.getBool(false, ref));
        bool.setBool(false, ref, true);
        assertTrue(bool.getBool(false, ref));
    }

    @Test
    public void testBitDirected() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        BooleanEncodedValue bool = new SimpleBooleanEncodedValue("access", true);
        bool.init(config);
        IntsRef ref = new IntsRef(1);
        bool.setBool(false, ref, false);
        bool.setBool(true, ref, true);

        assertFalse(bool.getBool(false, ref));
        assertTrue(bool.getBool(true, ref));
    }
}