package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StringEncodedValueTest {

    @Test
    public void testInit() {
        StringEncodedValue prop = new StringEncodedValue("highway", Arrays.asList("primary", "secondary"), "secondary");
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(2, prop.init(init));
        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        IntsRef ref = new IntsRef(1);
        // some invalid value should force default?
        ref.ints[0] = -1;
        assertEquals("secondary", prop.getString(false, ref));
    }
}