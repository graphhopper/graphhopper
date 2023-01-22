package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsTagParserTest {

    private final EncodingManager encodingManager = EncodingManager.create("roads");
    private final RoadsTagParser parser;

    public RoadsTagParserTest() {
        parser = new RoadsTagParser(encodingManager, new PMap());
        parser.init(new DateRangeParser());
    }

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        IntsRef flags = encodingManager.createEdgeFlags();
        parser.handleWayTags(flags, way);
        assertTrue(parser.getAverageSpeedEnc().getDecimal(false, flags) > 200);
    }

    @Test
    public void testHGV() {
        RoadsTagParser hgvParser = new RoadsTagParser(EncodingManager.create("roads"), new PMap("transportation_mode=HGV"));
        hgvParser.init(new DateRangeParser());

        assertEquals("[hgv, motor_vehicle, vehicle, access]", hgvParser.getRestrictions().toString());
    }
}