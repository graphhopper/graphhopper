package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.*;

public class IntEncodedValueTest {

    @Test
    public void testInvalidReverseAccess() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("test", 10, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setInt(true, new IntsRef(1), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectedValue() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("test", 10, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(1);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void multiIntsUsage() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("test", 31, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(2);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void padding() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("test", 30, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(2);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void testSignedInt() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("test", 31, false);
        BooleanEncodedValue sign = new SimpleBooleanEncodedValue("a");
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        prop.init(config);
        sign.init(config);

        IntsRef ref = new IntsRef(1);

        prop.setInt(false, ref, Integer.MAX_VALUE);
        sign.setBool(false, ref, true);
        assertEquals(Integer.MAX_VALUE, prop.getInt(false, ref));
        assertTrue(sign.getBool(false, ref));

        prop.setInt(false, ref, Integer.MAX_VALUE);
        sign.setBool(false, ref, false);
        assertEquals(Integer.MAX_VALUE, prop.getInt(false, ref));
        assertFalse(sign.getBool(false, ref));
    }
}