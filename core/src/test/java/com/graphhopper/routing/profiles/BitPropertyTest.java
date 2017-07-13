package com.graphhopper.routing.profiles;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;


public class BitPropertyTest {

    @Test
    public void testBit() {
        Property.InitializerConfig config = new Property.InitializerConfig();
        IntProperty intProp = new IntProperty("somevalue", 5);
        intProp.init(config);

        BitProperty bool = new BitProperty("access");
        bool.init(config);
        assertFalse(bool.fromStorageFormatToBool(bool.toStorageFormatFromBool(0, false)));
        assertTrue(bool.fromStorageFormatToBool(bool.toStorageFormatFromBool(0, true)));
    }
}