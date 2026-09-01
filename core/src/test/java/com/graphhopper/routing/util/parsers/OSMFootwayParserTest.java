package com.graphhopper.routing.util.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Footway;
import com.graphhopper.storage.IntsRef;

public class OSMFootwayParserTest {
    private EnumEncodedValue<Footway> footwayEnc;
    private OSMFootwayParser parser;

    @BeforeEach
    public void setUp() {
        footwayEnc = Footway.create();
        footwayEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMFootwayParser(footwayEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "footway");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Footway.MISSING, footwayEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("footway", "crossing");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Footway.CROSSING, footwayEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}