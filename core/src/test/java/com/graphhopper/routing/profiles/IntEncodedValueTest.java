package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntEncodedValueTest {

    @Test
    public void testInvalidReverseAccess() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setInt(true, new IntsRef(1), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectedValue() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(1);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }
}