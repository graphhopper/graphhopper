package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMPsvParserTest {

    @Test
    public void testYes() {
        EnumEncodedValue<Psv> encVal = Psv.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMPsvParser parser = new OSMPsvParser(encVal);
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Psv.MISSING, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Psv.YES, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "designated");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Psv.DESIGNATED, encVal.getEnum(false, 0, access));
    }

}
