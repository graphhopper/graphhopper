package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMHgvParserTest {
    private final int edgeId = 0;
    private EnumEncodedValue<Hgv> hgvEnc;
    private OSMHgvParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        hgvEnc = Hgv.create();
        hgvEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHgvParser(hgvEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv", "destination");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Hgv.DESTINATION, hgvEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testConditionalTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 3.5)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Hgv.NO, hgvEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        // for now we assume "hgv" to be only above 3.5
        readerWay.setTag("hgv:conditional", "delivery @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Hgv.MISSING, hgvEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}