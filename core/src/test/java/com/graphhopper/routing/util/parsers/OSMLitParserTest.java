package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMLitParserTest {
    private BooleanEncodedValue LitEnc;
    private OSMLitParser parser;

    @BeforeEach
    public void setUp() {
        LitEnc = Lit.create();
        LitEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMLitParser(LitEnc);
    }

    @Test
    public void testLitTags() {
        IntsRef relFlags = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "cycleway");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(false, LitEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("lit", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(true, LitEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("lit", "24/7");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(true, LitEnc.getBool(false, edgeId, edgeIntAccess));

    }
}
