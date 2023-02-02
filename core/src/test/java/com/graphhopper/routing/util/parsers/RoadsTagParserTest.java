package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayIntAccess;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.VehicleEncodedValues;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsTagParserTest {

    private final EncodingManager encodingManager = new EncodingManager.Builder().add(VehicleEncodedValues.roads(new PMap())).build();
    private final RoadsAverageSpeedParser parser;

    public RoadsTagParserTest() {
        parser = new RoadsAverageSpeedParser(encodingManager, new PMap());
    }

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, way, null);
        assertTrue(encodingManager.getDecimalEncodedValue(VehicleSpeed.key("roads")).getDecimal(false, edgeId, intAccess) > 200);
    }

}