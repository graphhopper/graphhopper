package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMMaxWeightParserTest {
    private final int edgeId = 0;
    private DecimalEncodedValue mwEnc;
    private OSMMaxWeightParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        mwEnc = MaxWeight.create();
        mwEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMMaxWeightParser(mwEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(5.0, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(25.4, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);
    }

    @Test
    public void testConditionalTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(7.5, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("hgv:conditional", "none @ (weight > 10t)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(10, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("hgv:conditional", "no@ (weight > 7)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(7, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("hgv:conditional", "no @ (maxweight > 6)"); // allow different tagging
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(6, mwEnc.getDecimal(false, edgeId, edgeIntAccess), .01);
    }
}