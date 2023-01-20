package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.RestrictionTagParser;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsTagParserTest {

    private final EncodingManager encodingManager = EncodingManager.create(VehicleSpeed.key("roads"));
    private final RoadsAverageSpeedParser parser;

    public RoadsTagParserTest() {
        parser = new RoadsAverageSpeedParser(encodingManager, new PMap());
    }

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        IntsRef flags = parser.handleWayTags(encodingManager.createEdgeFlags(), way, null);
        assertTrue(encodingManager.getDecimalEncodedValue(VehicleSpeed.key("roads")).getDecimal(false, flags) > 200);
    }
}