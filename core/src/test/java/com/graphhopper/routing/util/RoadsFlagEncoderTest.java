package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsFlagEncoderTest {

    private final EncodingManager encodingManager = EncodingManager.create("roads");
    private final RoadsFlagEncoder roadsEncoder = (RoadsFlagEncoder) encodingManager.getEncoder("roads");

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(roadsEncoder.getAccess(way).canSkip());

        way.setTag("highway", "motorway");
        assertTrue(roadsEncoder.getAccess(way).isWay());
        way.setTag("highway", "footway");
        assertTrue(roadsEncoder.getAccess(way).isWay());
    }

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        IntsRef flags = roadsEncoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(roadsEncoder.getAverageSpeedEnc().getDecimal(false, flags) > 200);
    }

}