package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OSMSeasonalRestrictedParserTest {
    private BooleanEncodedValue seasonalRestrictedEnc;
    private OSMSeasonalRestrictedParser parser;

    @BeforeEach
    public void setUp() {
        seasonalRestrictedEnc = SeasonalRestricted.create();
        seasonalRestrictedEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSeasonalRestrictedParser(seasonalRestrictedEnc);
    }

    @Test
    public void testSeasonalRestrictedTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertFalse(seasonalRestrictedEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("access:conditional", "no @ Winter");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertTrue(seasonalRestrictedEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("access:conditional", "no @ Nov-Apr");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertTrue(seasonalRestrictedEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("access:conditional", "no @ (Nov-Apr; May 19:30-06:00; Jun-Aug 20:30-05:30; Sep-Oct 19:30-06:00)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertTrue(seasonalRestrictedEnc.getBool(false, edgeId, edgeIntAccess));
    }
}
