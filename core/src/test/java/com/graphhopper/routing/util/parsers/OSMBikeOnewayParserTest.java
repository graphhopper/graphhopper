package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OSMBikeOnewayParserTest {
    private final BooleanEncodedValue onewayEnc = BikeOneway.create();
    private final OSMBikeOnewayParser parser = new OSMBikeOnewayParser(onewayEnc, Roundabout.create());

    public OSMBikeOnewayParserTest() {
        onewayEnc.init(new EncodedValue.InitializerConfig());
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");
        assertAccess(way, true, true);

        way.setTag("oneway", "yes");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway:bicycle", "yes");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "-1");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:right:oneway", "-1");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "no");
        assertAccess(way, false, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        assertAccess(way, true, false);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle:backward", "no");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        assertAccess(way, true, false);

        way.setTag("bicycle:backward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "-1");
        way.setTag("bicycle:forward", "yes");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        assertAccess(way, true, true);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite_lane");
        assertAccess(way, true, true);
    }

    private void assertAccess(ReaderWay way, boolean fwd, boolean bwd) {
        IntsRef edgeFlags = new IntsRef(1);
        IntsRef relationFlags = new IntsRef(1);
        parser.handleWayTags(edgeFlags, way, relationFlags);
        if (fwd) assertTrue(onewayEnc.getBool(false, edgeFlags));
        if (bwd) assertTrue(onewayEnc.getBool(true, edgeFlags));
    }

}