package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMRoadClassParserTest {

    private TagParserManager tpm;
    private IntsRef relFlags;
    private EnumEncodedValue<RoadClass> rcEnc;
    private OSMRoadClassParser parser;

    @BeforeEach
    public void setUp() {
        parser = new OSMRoadClassParser();
        tpm = new TagParserManager.Builder().add(parser).build();
        relFlags = tpm.createRelationFlags();
        rcEnc = tpm.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.PRIMARY, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = tpm.createEdgeFlags();
        readerWay.setTag("highway", "unknownstuff");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = tpm.createEdgeFlags();
        readerWay.setTag("highway", "motorway_link");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.MOTORWAY, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testIgnore() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = tpm.createEdgeFlags();
        readerWay.setTag("route", "ferry");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = tpm.createEdgeFlags();
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }
}