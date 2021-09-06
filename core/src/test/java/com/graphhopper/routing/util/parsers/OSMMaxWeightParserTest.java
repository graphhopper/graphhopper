package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.MaxWeight;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OSMMaxWeightParserTest {

    private EncodingManager em;
    private DecimalEncodedValue mwEnc;
    private OSMMaxWeightParser parser;

    @Before
    public void setUp() {
        parser = new OSMMaxWeightParser();
        em = new EncodingManager.Builder().add(parser).build();
        mwEnc = em.getDecimalEncodedValue(MaxWeight.KEY);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(intsRef, readerWay, false, em.createRelationFlags());
        assertEquals(5.0, mwEnc.getDecimal(false, intsRef), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intsRef = em.createEdgeFlags();
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(intsRef, readerWay, false, em.createRelationFlags());
        assertEquals(mwEnc.getMaxDecimal(), mwEnc.getDecimal(false, intsRef), .01);
    }
}