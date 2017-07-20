package com.graphhopper.routing.profiles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntEncodedValueTest {

    @Test
    public void testInvalidReverseAccess() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.toStorageFormat(true, 0, -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectedValue() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        int storable = prop.toStorageFormat(false, 0, 10);
        storable = prop.toStorageFormat(true, storable, 20);
        assertEquals(10, prop.fromStorageFormatToInt(false, storable));
        assertEquals(20, prop.fromStorageFormatToInt(true, storable));
    }
}