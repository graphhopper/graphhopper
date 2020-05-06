package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OSMHazmatWaterParserTest {

    private EncodingManager em;
    private EnumEncodedValue<HazmatWater> hazWaterEnc;
    private OSMHazmatWaterParser parser;
    private IntsRef relFlags;

    @Before
    public void setUp() {
        parser = new OSMHazmatWaterParser();
        em = new EncodingManager.Builder().add(parser).build();
        relFlags = em.createRelationFlags();
        hazWaterEnc = em.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "no");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(HazmatWater.NO, hazWaterEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat:water", "permissive");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(HazmatWater.PERMISSIVE, hazWaterEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));
    }
}