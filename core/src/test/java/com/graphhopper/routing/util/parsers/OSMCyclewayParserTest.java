package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.ev.Cycleway.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMCyclewayParserTest {
    private EnumEncodedValue<Cycleway> cyclewayEnc;
    private OSMCyclewayParser parser;

    @BeforeEach
    public void setUp() {
        cyclewayEnc = Cycleway.create();
        cyclewayEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMCyclewayParser(cyclewayEnc);
    }

    private void assertValue(Cycleway expectedFwd, Cycleway expectedBwd, ReaderWay way) {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(0, edgeIntAccess, way, new IntsRef(2));
        assertEquals(expectedFwd, cyclewayEnc.getEnum(false, 0, edgeIntAccess));
        assertEquals(expectedBwd, cyclewayEnc.getEnum(true, 0, edgeIntAccess));
    }

    @Test
    public void testMissing() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertValue(MISSING, MISSING, way);
    }

    @Test
    public void testGenericCycleway_setsBothDirections() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "lane");
        assertValue(LANE, LANE, way);
    }

    @Test
    public void testDirectionalKeys() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway:right", "track");
        assertValue(TRACK, MISSING, way);

        way = new ReaderWay(1);
        way.setTag("cycleway:left", "lane");
        assertValue(MISSING, LANE, way);

        way = new ReaderWay(1);
        way.setTag("cycleway:both", "shared_lane");
        assertValue(SHARED_LANE, SHARED_LANE, way);
    }

    @Test
    public void testLeftAndRight_independentDirections() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway:right", "track");
        way.setTag("cycleway:left", "lane");
        assertValue(TRACK, LANE, way);
    }

    @Test
    public void testOpposite_setsReverseOnly() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "opposite_lane");
        assertValue(MISSING, LANE, way);

        way = new ReaderWay(1);
        way.setTag("cycleway", "opposite_track");
        assertValue(MISSING, TRACK, way);
    }

    @Test
    public void testOppositeAlone_isIgnored() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "opposite");
        assertValue(MISSING, MISSING, way);
    }

    @Test
    public void testBetterValueWins_sameDirection() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "no");
        way.setTag("cycleway:right", "track");
        assertValue(TRACK, NO, way);
    }

    @Test
    public void testUnknownValue() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "something_unknown");
        assertValue(MISSING, MISSING, way);
    }

    @Test
    public void testSynonyms() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("cycleway", "sidepath");
        assertValue(SEPARATE, SEPARATE, way);

        way = new ReaderWay(1);
        way.setTag("cycleway", "crossing");
        assertValue(TRACK, TRACK, way);

        way = new ReaderWay(1);
        way.setTag("cycleway", "share_busway");
        assertValue(SHARED_LANE, SHARED_LANE, way);

        way = new ReaderWay(1);
        way.setTag("cycleway", "shared");
        assertValue(SHARED_LANE, SHARED_LANE, way);
    }
}
