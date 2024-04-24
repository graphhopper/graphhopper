package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumEncodedValueTest {

    @Test
    public void testInit() {
        EnumEncodedValue<RoadClass> prop = RoadClass.create();
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(5, prop.init(init));
        assertEquals(5, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        EdgeBytesAccess intAccess = new EdgeBytesAccessArray(4);
        // default if empty
        intAccess.setInt(0, 0, 0);
        assertEquals(RoadClass.OTHER, prop.getEnum(false, 0, intAccess));

        prop.setEnum(false, 0, intAccess, RoadClass.SECONDARY);
        assertEquals(RoadClass.SECONDARY, prop.getEnum(false, 0, intAccess));
    }

    @Test
    public void testSize() {
        assertEquals(3, 32 - Integer.numberOfLeadingZeros(7 - 1));
        assertEquals(3, 32 - Integer.numberOfLeadingZeros(8 - 1));
        assertEquals(4, 32 - Integer.numberOfLeadingZeros(9 - 1));
        assertEquals(4, 32 - Integer.numberOfLeadingZeros(16 - 1));
        assertEquals(5, 32 - Integer.numberOfLeadingZeros(17 - 1));
    }
}
