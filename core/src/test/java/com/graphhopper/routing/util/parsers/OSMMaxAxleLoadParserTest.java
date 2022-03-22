package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.MaxAxleLoad;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMMaxAxleLoadParserTest {

    private TagParserManager tpm;
    private DecimalEncodedValue malEnc;
    private OSMMaxAxleLoadParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        parser = new OSMMaxAxleLoadParser();
        tpm = new TagParserManager.Builder().add(parser).build();
        relFlags = tpm.createRelationFlags();
        malEnc = tpm.getDecimalEncodedValue(MaxAxleLoad.KEY);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        readerWay.setTag("maxaxleload", "11.5");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(11.5, malEnc.getDecimal(false, intsRef), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("maxaxleload", "80");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(malEnc.getMaxDecimal(), malEnc.getDecimal(false, intsRef), .01);
    }

    @Test
    public void testRounding() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        readerWay.setTag("maxaxleload", "4.8");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(5.0, malEnc.getDecimal(false, intsRef), .01);

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("maxaxleload", "3.6");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(3.5, malEnc.getDecimal(false, intsRef), .01);

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("maxaxleload", "2.4");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(2.5, malEnc.getDecimal(false, intsRef), .01);
    }

    @Test
    public void testNoLimit() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Double.POSITIVE_INFINITY, malEnc.getDecimal(false, intsRef), .01);
    }
}