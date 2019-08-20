package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.MaxWeight;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

public class OSMMaxWeightParserTest {

    private EncodingManager em;
    private DecimalEncodedValue mwEnc;
    private OSMMaxWeightParser parser;

    @Before
    public void setUp() {
        parser = new OSMMaxWeightParser();
        em = new EncodingManager.Builder(4).add(parser).build();
        mwEnc = em.getDecimalEncodedValue(MaxWeight.KEY);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(5.0, mwEnc.getDecimal(false, intsRef), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intsRef = em.createEdgeFlags();
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(mwEnc.getMaxDecimal(), mwEnc.getDecimal(false, intsRef), .01);
    }
}