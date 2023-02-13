package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.GetOffBike;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMGetOffBikeParserTest {
    private final BooleanEncodedValue offBikeEnc = GetOffBike.create();
    private final OSMGetOffBikeParser parser = new OSMGetOffBikeParser(offBikeEnc, Roundabout.create());

    public OSMGetOffBikeParserTest() {
        offBikeEnc.init(new EncodedValue.InitializerConfig());
    }

    @Test
    public void testHandleCommonWayTags() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        assertGetOffBike(way, true, true);

        way.setTag("highway", "footway");
        assertGetOffBike(way, true, true);

        way.setTag("highway", "footway");
        way.setTag("surface", "pebblestone");
        assertGetOffBike(way, true, true);

        way.setTag("highway", "residential");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("bicycle", "yes");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("surface", "grass");
        assertGetOffBike(way, true, true);
        way.setTag("bicycle", "yes");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "designated");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "official");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "permissive");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("railway", "platform");
        assertGetOffBike(way, true, true);

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("bicycle", "dismount");
        assertGetOffBike(way, true, true);

        way = new ReaderWay(1);
        way.setTag("highway", "platform");
        way.setTag("bicycle", "yes");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertGetOffBike(way, true, true);

        way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("surface", "concrete");
        assertGetOffBike(way, true, true);
        way.setTag("bicycle", "yes");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "designated");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "official");
        assertGetOffBike(way, false, false);
        way.setTag("bicycle", "permissive");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "track");
        assertGetOffBike(way, false, false);

        way = new ReaderWay(1);
        way.setTag("highway", "path");
        way.setTag("foot", "designated");
        assertGetOffBike(way, true, true);
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        assertGetOffBike(way, false, false);

        way.setTag("oneway", "yes");
        assertGetOffBike(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway:bicycle", "yes");
        assertGetOffBike(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "no");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "-1");
        assertGetOffBike(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "no");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "-1");
        assertGetOffBike(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        assertGetOffBike(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "no");
        assertGetOffBike(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        assertGetOffBike(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        assertGetOffBike(way, false, true);

        way.setTag("bicycle:backward", "yes");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "yes");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "-1");
        way.setTag("bicycle:forward", "yes");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        assertGetOffBike(way, false, false);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertGetOffBike(way, false, false);
    }

    private void assertGetOffBike(ReaderWay way, boolean fwd, boolean bwd) {
        IntsRef edgeFlags = new IntsRef(1);
        IntsRef relationFlags = new IntsRef(1);
        parser.handleWayTags(edgeFlags, way, relationFlags);
        if (fwd) assertTrue(offBikeEnc.getBool(false, edgeFlags));
        if (bwd) assertTrue(offBikeEnc.getBool(true, edgeFlags));
    }
}