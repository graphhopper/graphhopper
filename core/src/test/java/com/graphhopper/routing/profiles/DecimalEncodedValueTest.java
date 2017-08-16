package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecimalEncodedValueTest {

    @Test
    public void testInit() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 2, false);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(1);
        prop.setDecimal(false, ref, 10d);
        assertEquals(10d, prop.getDecimal(false, ref), 0.1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValue("test", 10, 50, 5, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setDecimal(false, new IntsRef(1), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}