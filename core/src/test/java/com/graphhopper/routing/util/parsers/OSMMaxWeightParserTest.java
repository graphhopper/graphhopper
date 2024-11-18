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
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        assertEquals(5.0, getMaxWeight(readerWay), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        readerWay.setTag("maxweight", "54");
        assertEquals(51, getMaxWeight(readerWay), .01);
    }

    @Test
    public void testConditionalTags() {
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("access:conditional", "no @ (weight > 7.5)");
        assertEquals(7.5, getMaxWeight(readerWay), .01);

        readerWay.setTag("access:conditional", "no @ weight > 7");
        assertEquals(7, getMaxWeight(readerWay), .01);

        readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 7.5)");
        assertEquals(7.5, getMaxWeight(readerWay), .01);

        readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "none @ (weight > 10t)");
        assertEquals(10, getMaxWeight(readerWay), .01);

        readerWay.setTag("hgv:conditional", "no@ (weight > 7)");
        assertEquals(7, getMaxWeight(readerWay), .01);

        readerWay.setTag("hgv:conditional", "no @ (maxweight > 6)"); // allow different tagging
        assertEquals(6, getMaxWeight(readerWay), .01);
    }

    double getMaxWeight(ReaderWay readerWay) {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        return mwEnc.getDecimal(false, edgeId, edgeIntAccess);
    }
}
