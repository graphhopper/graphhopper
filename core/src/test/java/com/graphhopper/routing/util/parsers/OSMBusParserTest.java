package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMBusParserTest {

    @Test
    public void testYes() {
        EnumEncodedValue<Bus> encVal = Bus.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMBusParser parser = new OSMBusParser(encVal);
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.MISSING, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "yes");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.YES, encVal.getEnum(false, 0, access));
    }

    @Test
    public void testDesignated() {
        EnumEncodedValue<Bus> encVal = Bus.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMBusParser parser = new OSMBusParser(encVal);
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("access", "no");
        way.setTag("highway", "busway");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.DESIGNATED, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "designated");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.DESIGNATED, encVal.getEnum(false, 0, access));
    }

    @Test
    public void testNo() {
        EnumEncodedValue<Bus> encVal = Bus.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMBusParser parser = new OSMBusParser(encVal);
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.MISSING, encVal.getEnum(false, 0, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "no");
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.NO, encVal.getEnum(false, 0, access));
    }

    @Test
    public void testNodeAccess() {
        EnumEncodedValue<Bus> encVal = Bus.create();
        encVal.init(new EncodedValue.InitializerConfig());
        OSMBusParser parser = new OSMBusParser(encVal);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        Map<String, Object> tags1 = new HashMap<>();
        tags1.put("bus", "no");
        Map<String, Object> tags2 = new HashMap<>();
        tags2.put("bus", "designated");
        way.setTag("node_tags", Arrays.asList(tags1, tags2));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(0, access, way, null);
        assertEquals(Bus.NO, encVal.getEnum(false, 0, access));
    }
}
