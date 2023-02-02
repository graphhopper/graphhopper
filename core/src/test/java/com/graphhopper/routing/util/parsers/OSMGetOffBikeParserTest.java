package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMGetOffBikeParserTest {
    private final BooleanEncodedValue offBikeEnc = GetOffBike.create();
    private final OSMGetOffBikeParser parser = new OSMGetOffBikeParser(offBikeEnc);

    public OSMGetOffBikeParserTest() {
        offBikeEnc.init(new EncodedValue.InitializerConfig());
    }

    @Test
    public void testHandleCommonWayTags() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        assertTrue(isGetOffBike(way));

        way.setTag("highway", "footway");
        assertTrue(isGetOffBike(way));

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        assertTrue(isGetOffBike(way));

        way.setTag("highway", "residential");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("surface", "grass");
        assertTrue(isGetOffBike(way));
        way.setTag("bicycle", "yes");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "designated");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "official");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "permissive");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("railway", "platform");
        assertTrue(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertTrue(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "platform");
        way.setTag("bicycle", "yes");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertTrue(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("surface", "concrete");
        assertTrue(isGetOffBike(way));
        way.setTag("bicycle", "yes");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "designated");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "official");
        assertFalse(isGetOffBike(way));
        way.setTag("bicycle", "permissive");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        assertFalse(isGetOffBike(way));

        way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("foot", "designated");
        assertTrue(isGetOffBike(way));
    }

    private boolean isGetOffBike(ReaderWay way) {
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        IntsRef relationFlags = new IntsRef(1);
        parser.handleWayTags(edgeId, intAccess, way, relationFlags);
        return offBikeEnc.getBool(false, edgeId, intAccess);
    }
}