package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.GetOffBike;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OSMGetOffBikeParserTest {

    private EncodingManager em = EncodingManager.start().add(new BikeFlagEncoder()).build();
    private BooleanEncodedValue offBikeEnc = em.getBooleanEncodedValue(GetOffBike.KEY);
    private EnumEncodedValue<RoadClass> roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);

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
        way.setTag("highway", "cycleway");
        assertEquals(getRoadClass(way), RoadClass.CYCLEWAY);

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

    private RoadClass getRoadClass(ReaderWay way) {
        IntsRef edgeFlags = em.handleWayTags(way, new EncodingManager.AcceptWay().put("bike", EncodingManager.Access.WAY), em.createRelationFlags());
        return roadClassEnc.getEnum(false, edgeFlags);
    }

    private boolean isGetOffBike(ReaderWay way) {
        IntsRef edgeFlags = em.handleWayTags(way, new EncodingManager.AcceptWay().put("bike", EncodingManager.Access.WAY), em.createRelationFlags());
        return offBikeEnc.getBool(false, edgeFlags);
    }
}