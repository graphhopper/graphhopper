package com.graphhopper.routing.util.parsers;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.MaxAxleLoad;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMMaxAxleLoadParserTest {

    private EncodingManager em;
    private DecimalEncodedValue malEnc;
    private OSMMaxAxleLoadParser parser;

    @Before
    public void setUp() {
        parser = new OSMMaxAxleLoadParser();
        em = new EncodingManager.Builder(4).add(parser).build();
        malEnc = em.getDecimalEncodedValue(MaxAxleLoad.KEY);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("maxaxleload", "11.5");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(11.5, malEnc.getDecimal(false, intsRef), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intsRef = em.createEdgeFlags();
        readerWay.setTag("maxaxleload", "80");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(malEnc.getMaxDecimal(), malEnc.getDecimal(false, intsRef), .01);
    }

    @Test
    public void testRounding() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("maxaxleload", "4.8");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(5.0, malEnc.getDecimal(false, intsRef), .01);
        
        intsRef = em.createEdgeFlags();
        readerWay.setTag("maxaxleload", "3.6");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(3.5, malEnc.getDecimal(false, intsRef), .01);
        
        intsRef = em.createEdgeFlags();
        readerWay.setTag("maxaxleload", "2.4");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(2.5, malEnc.getDecimal(false, intsRef), .01);
    }
    
    @Test
    public void testNoLimit() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Double.POSITIVE_INFINITY, malEnc.getDecimal(false, intsRef), .01);
    }
}