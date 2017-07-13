package com.graphhopper.routing.profiles;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;


public class BitEncodedValueTest {

    @Test
    public void testBit() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        IntEncodedValue intProp = new IntEncodedValue("somevalue", 5);
        intProp.init(config);

        BitEncodedValue bool = new BitEncodedValue("access");
        bool.init(config);
        assertFalse(bool.fromStorageFormatToBool(bool.toStorageFormatFromBool(0, false)));
        assertTrue(bool.fromStorageFormatToBool(bool.toStorageFormatFromBool(0, true)));
    }
}