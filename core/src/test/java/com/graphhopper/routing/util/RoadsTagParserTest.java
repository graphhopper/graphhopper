package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.RestrictionTagParser;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.util.parsers.RoadsAccessParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsTagParserTest {

    private final EncodingManager encodingManager = EncodingManager.create(VehicleSpeed.key("roads"));
    private final RoadsAccessParser parser;

    public RoadsTagParserTest() {
        parser = new RoadsAccessParser(encodingManager, new PMap());
    }

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        IntsRef flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way, null);
        assertTrue(encodingManager.getDecimalEncodedValue(VehicleSpeed.key("roads")).getDecimal(false, flags) > 200);
    }

    @Test
    public void testHGV() {
        RestrictionTagParser hgvParser = (RestrictionTagParser) new DefaultTagParserFactory().create(encodingManager, "roads_turn_costs");
        assertEquals("[hgv, motor_vehicle, vehicle, access]", hgvParser.getVehicleTypes().toString());
    }
}