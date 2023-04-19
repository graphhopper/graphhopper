package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTollParserTest {
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @BeforeEach
    public void setUp() {
        tollEnc = Toll.create();
        tollEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTollParser(tollEnc);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef relFlags = new IntsRef(2);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.MISSING, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}