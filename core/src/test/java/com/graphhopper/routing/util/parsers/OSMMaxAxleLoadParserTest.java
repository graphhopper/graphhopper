package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMMaxAxleLoadParserTest {
    private DecimalEncodedValue malEnc;
    private OSMMaxAxleLoadParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        malEnc = MaxAxleLoad.create();
        malEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMMaxAxleLoadParser(malEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("maxaxleload", "11.5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(11.5, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("maxaxleload", "80");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(63.0, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);
    }

    @Test
    public void testRounding() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("maxaxleload", "4.8");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(5.0, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("maxaxleload", "3.6");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(3.5, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("maxaxleload", "2.4");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(2.5, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);
    }

    @Test
    public void testNoLimit() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Double.POSITIVE_INFINITY, malEnc.getDecimal(false, edgeId, edgeIntAccess), .01);
    }
}