package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatWaterParserTest {

    private TagParserManager tpm;
    private EnumEncodedValue<HazmatWater> hazWaterEnc;
    private OSMHazmatWaterParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        parser = new OSMHazmatWaterParser();
        tpm = new TagParserManager.Builder().add(parser).build();
        relFlags = tpm.createRelationFlags();
        hazWaterEnc = tpm.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        readerWay.setTag("hazmat:water", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatWater.NO, hazWaterEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("hazmat:water", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("hazmat:water", "permissive");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatWater.PERMISSIVE, hazWaterEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, intsRef));
    }
}