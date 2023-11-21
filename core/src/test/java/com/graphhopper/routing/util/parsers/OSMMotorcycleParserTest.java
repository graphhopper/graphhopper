package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OSMMotorcycleParserTest {

    @Test
    public void testYes() {
        EnumEncodedValue<Motorcycle> encVal = Motorcycle.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMMotorcycleParser parser = new OSMMotorcycleParser(encVal);
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Motorcycle.MISSING, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("motorcycle", "yes");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Motorcycle.YES, encVal.getEnum(false, 0, access));
    }
}
