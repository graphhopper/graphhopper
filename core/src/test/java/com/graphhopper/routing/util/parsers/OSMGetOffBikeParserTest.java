package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.GetOffBike;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.VehicleEncodedValues;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMGetOffBikeParserTest {
    private final BooleanEncodedValue offBikeEnc = GetOffBike.create();
    private final BikeAccessParser accessParser;
    private final OSMGetOffBikeParser getOffParser;

    public OSMGetOffBikeParserTest() {
        EncodingManager em = new EncodingManager.Builder().add(offBikeEnc).add(VehicleEncodedValues.bike(new PMap()).getAccessEnc()).build();
        accessParser = new BikeAccessParser(em, new PMap());
        accessParser.init(new DateRangeParser());
        getOffParser = new OSMGetOffBikeParser(offBikeEnc, accessParser.getAccessEnc());
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

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");

        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        IntsRef rel = new IntsRef(1);
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, new IntsRef(1));
        getOffParser.handleWayTags(edgeId, edgeIntAccess, way, new IntsRef(1));

        assertFalse(offBikeEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(offBikeEnc.getBool(true, edgeId, edgeIntAccess));
    }

    private boolean isGetOffBike(ReaderWay way) {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        IntsRef rel = new IntsRef(1);
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, rel);
        getOffParser.handleWayTags(edgeId, edgeIntAccess, way, rel);
        return offBikeEnc.getBool(false, edgeId, edgeIntAccess);
    }
}