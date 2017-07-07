package com.graphhopper.routing.profiles;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StringPropertyTest {

    @Test
    public void testInit() {
        StringProperty prop = new StringProperty("highway", Arrays.asList("primary", "secondary"), "secondary");
        Property.InitializerConfig init = new Property.InitializerConfig();
        prop.init(init);

        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(2, init.shift);
        assertEquals(1, init.propertyIndex);
        assertEquals("secondary", prop.fromStorageFormatToString(-1));
    }
}