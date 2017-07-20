package com.graphhopper.routing.profiles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecimalEncodedValueTest {

    @Test
    public void testInit() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 2, false);
        prop.init(new EncodedValue.InitializerConfig());
        assertEquals(10d, prop.fromStorageFormatToDouble(false, prop.toStorageFormatFromDouble(false, 0, 10d)), 0.1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 5, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.toStorageFormatFromDouble(false, 0, -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}