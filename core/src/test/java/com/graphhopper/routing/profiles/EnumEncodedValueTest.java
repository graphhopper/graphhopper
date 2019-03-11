package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnumEncodedValueTest {

    @Test
    public void testInit() {
        EnumEncodedValue prop = new EnumEncodedValue("road_class", RoadClass.values());
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(5, prop.init(init));
        assertEquals(5, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        IntsRef ref = new IntsRef(1);
        // default if empty
        ref.ints[0] = 0;
        assertEquals(RoadClass.OTHER, prop.getObject(false, ref));

        prop.setObject(false, ref, RoadClass.SECONDARY);
        assertEquals(RoadClass.SECONDARY, prop.getObject(false, ref));
    }
}