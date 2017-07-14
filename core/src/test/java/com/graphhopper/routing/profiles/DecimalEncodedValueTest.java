package com.graphhopper.routing.profiles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecimalEncodedValueTest {

    @Test
    public void testInit() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 2);
        prop.init(new EncodedValue.InitializerConfig());
        assertEquals(10d, prop.fromStorageFormatToDouble(prop.toStorageFormatFromDouble(0, 10d)), 0.1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 5);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.fromStorageFormatToDouble(prop.toStorageFormatFromDouble(0, -1));
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}