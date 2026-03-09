package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.ev.Sidewalk.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMSidewalkParserTest {
    private EnumEncodedValue<Sidewalk> sidewalkEnc;
    private OSMSidewalkParser parser;

    @BeforeEach
    public void setUp() {
        sidewalkEnc = Sidewalk.create();
        sidewalkEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSidewalkParser(sidewalkEnc);
    }

    private void assertValue(Sidewalk expectedFwd, Sidewalk expectedBwd, ReaderWay way) {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(0, edgeIntAccess, way, new IntsRef(2));
        assertEquals(expectedFwd, sidewalkEnc.getEnum(false, 0, edgeIntAccess));
        assertEquals(expectedBwd, sidewalkEnc.getEnum(true, 0, edgeIntAccess));
    }

    @Test
    public void testMissing() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        assertValue(MISSING, MISSING, way);
    }

    @Test
    public void testMainTag_directionalValues() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk", "both");
        assertValue(YES, YES, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk", "right");
        assertValue(YES, MISSING, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk", "left");
        assertValue(MISSING, YES, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk", "yes");
        assertValue(YES, YES, way);
    }

    @Test
    public void testMainTag_noAndSeparate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk", "no");
        assertValue(NO, NO, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk", "none");
        assertValue(NO, NO, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk", "separate");
        assertValue(SEPARATE, SEPARATE, way);
    }

    @Test
    public void testDirectionalKeys() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk:right", "yes");
        assertValue(YES, MISSING, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk:left", "yes");
        assertValue(MISSING, YES, way);

        way = new ReaderWay(1);
        way.setTag("sidewalk:both", "separate");
        assertValue(SEPARATE, SEPARATE, way);
    }

    @Test
    public void testDirectionalKeys_overrideMainTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk", "both");
        way.setTag("sidewalk:right", "separate");
        assertValue(SEPARATE, YES, way);
    }

    @Test
    public void testLeftAndRight_independent() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk:right", "yes");
        way.setTag("sidewalk:left", "no");
        assertValue(YES, NO, way);
    }

    @Test
    public void testUnknownValue() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("sidewalk", "something_unknown");
        assertValue(MISSING, MISSING, way);
    }
}
