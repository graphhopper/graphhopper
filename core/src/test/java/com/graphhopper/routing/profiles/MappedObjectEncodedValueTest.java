package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MappedObjectEncodedValueTest {

    @Test
    public void testInit() {
        List<RoadClass> list = RoadClass.create("primary", "secondary");
        RoadClass primary = list.get(0);
        RoadClass secondary = list.get(1);
        MappedObjectEncodedValue prop = new MappedObjectEncodedValue("road_class", list);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(2, prop.init(init));
        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        IntsRef ref = new IntsRef(1);
        // default if empty
        ref.ints[0] = 0;
        assertEquals(primary, prop.getObject(false, ref));

        prop.setObject(false, ref, secondary);
        assertEquals(secondary, prop.getObject(false, ref));
    }
}