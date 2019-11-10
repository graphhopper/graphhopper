package com.graphhopper.routing.util.parsers;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.HazmatWater;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMHazmatWaterParserTest {

    private EncodingManager em;
    private EnumEncodedValue<HazmatWater> hazWaterEnc;
    private OSMHazmatWaterParser parser;

    @Before
    public void setUp() {
        parser = new OSMHazmatWaterParser();
        em = new EncodingManager.Builder().add(parser).build();
        hazWaterEnc = em.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "no");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(HazmatWater.NO, hazWaterEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "permissive");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(HazmatWater.PERMISSIVE, hazWaterEnc.getEnum(false, intsRef));
    }
    
    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));
    }
}