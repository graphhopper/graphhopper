package com.graphhopper.routing.profiles;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StringEncodedValueTest {

    @Test
    public void testInit() {
        StringEncodedValue prop = new StringEncodedValue("highway", Arrays.asList("primary", "secondary"), "secondary");
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        prop.init(init);

        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(2, init.shift);
        assertEquals(1, init.propertyIndex);
        assertEquals("secondary", prop.fromStorageFormatToString(-1));
    }
}