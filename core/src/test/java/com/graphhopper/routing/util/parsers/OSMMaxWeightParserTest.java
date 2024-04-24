package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMMaxWeightParserTest {
    private final int edgeId = 0;
    private DecimalEncodedValue mwEnc;
    private OSMMaxWeightParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        mwEnc = MaxWeight.create();
        mwEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMMaxWeightParser(mwEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testSimpleTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(5.0, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(25.4, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);
    }

    @Test
    public void testConditionalTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(7.5, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hgv:conditional", "none @ (weight > 10t)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(10, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hgv:conditional", "no@ (weight > 7)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(7, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hgv:conditional", "no @ (maxweight > 6)"); // allow different tagging
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(6, mwEnc.getDecimal(false, edgeId, edgeAccess), .01);
    }
}
