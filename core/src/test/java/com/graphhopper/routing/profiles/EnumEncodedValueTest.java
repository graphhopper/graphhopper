package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EnumEncodedValueTest {

    enum TestEnum {
        PRIMARY, SECONDARY
    }

    @Test
    public void testInit() {
        EnumEncodedValueImpl<TestEnum> prop = new EnumEncodedValueImpl<>("highway", TestEnum.values(), TestEnum.SECONDARY);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(2, prop.init(init));
        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        IntsRef ref = new IntsRef(1);
        // some invalid value should force default?
        ref.ints[0] = -1;

        TestEnum testEnum = prop.getEnum(false, ref);
        assertTrue(TestEnum.SECONDARY.equals(testEnum));
        assertFalse(RoadClass.SECONDARY.equals(testEnum));
    }

    @Test
    public void testGet() {
        EnumEncodedValue prop = RoadClass.create();
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertTrue(prop.init(init) >= 5);

        IntsRef edgeFlags = new IntsRef(1);
        prop.setEnum(false, edgeFlags, RoadClass.PRIMARY);
        assertEquals("primary", prop.getEnum(false, edgeFlags).toString());
        assertEquals(RoadClass.PRIMARY.ordinal(), prop.indexOf("primary"));

        prop.setEnum(false, edgeFlags, RoadClass.SECONDARY);
        assertEquals("secondary", prop.getEnum(false, edgeFlags).toString());
    }
}